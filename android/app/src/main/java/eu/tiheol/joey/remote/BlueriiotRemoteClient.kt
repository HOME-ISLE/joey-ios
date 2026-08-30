package eu.tiheol.joey.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BlueriiotRemoteClient {
    data class Session(
        val accessKey: String,
        val secretKey: String,
        val sessionToken: String
    )

    data class Pool(val id: String, val name: String)
    data class Device(val serial: String)
    data class Measurements(
        val temperature: Double?,
        val ph: Double?,
        val orp: Double?,
        val conductivity: Double?,
        val timestamp: String?
    )

    private val baseUrl = "https://api.riiotlabs.com/prod/"
    private val region = "eu-west-1"
    private val service = "execute-api"

    fun login(email: String, password: String): Session {
        val url = URL(baseUrl + "user/login")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BlueConnect/3.2.1")
        }
        val body = JSONObject().put("email", email).put("password", password).toString()
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val response = readResponse(connection)
        val json = JSONObject(response)
        val credentials = json.getJSONObject("credentials")
        return Session(
            accessKey = credentials.getString("access_key"),
            secretKey = credentials.getString("secret_key"),
            sessionToken = credentials.getString("session_token")
        )
    }

    fun getPools(session: Session): List<Pool> {
        val json = JSONObject(signedGet("swimming_pool/", session))
        val data = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val poolObject = item.optJSONObject("swimming_pool")
                val id = item.optString("swimming_pool_id").ifBlank {
                    poolObject?.optString("swimming_pool_id").orEmpty()
                }
                val name = item.optString("name").ifBlank {
                    poolObject?.optString("name").orEmpty()
                }
                if (id.isNotBlank()) add(Pool(id, name.ifBlank { "Mon bassin" }))
            }
        }
    }

    fun getDevices(session: Session, poolId: String): List<Device> {
        val path = "swimming_pool/$poolId/blue/"
        val json = JSONObject(signedGet(path, session))
        val data = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val serial = item.optString("blue_device_serial").ifBlank {
                    item.optJSONObject("blue_device")?.optString("serial").orEmpty()
                }
                if (serial.isNotBlank()) add(Device(serial))
            }
        }
    }

    fun getLastMeasurements(session: Session, poolId: String, serial: String): Measurements {
        val path = "swimming_pool/$poolId/blue/$serial/lastMeasurements"
        val json = JSONObject(signedGet(path, session, "mode=blue_and_strip"))
        val data = json.optJSONArray("data")
        var temperature: Double? = null
        var ph: Double? = null
        var orp: Double? = null
        var conductivity: Double? = null
        var timestamp: String? = json.optString("last_blue_measure_timestamp").takeIf { it.isNotBlank() }
        if (data != null) {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val value = if (item.has("value")) item.optDouble("value") else Double.NaN
                if (value.isNaN()) continue
                when (item.optString("name").lowercase(Locale.ROOT)) {
                    "temperature" -> temperature = value
                    "ph" -> ph = value
                    "orp" -> orp = value
                    "conductivity" -> conductivity = value
                }
                if (timestamp == null) timestamp = item.optString("timestamp").takeIf { it.isNotBlank() }
            }
        }
        return Measurements(temperature, ph, orp, conductivity, timestamp)
    }

    private fun signedGet(path: String, session: Session, query: String = ""): String {
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val timestampFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dateStamp = dateFormat.format(now)
        val amzDate = timestampFormat.format(now)
        val host = "api.riiotlabs.com"
        val canonicalUri = "/prod/$path"
        val canonicalQuery = query
        val payloadHash = sha256Hex("")
        val canonicalHeaders = "host:$host\n" +
            "x-amz-date:$amzDate\n" +
            "x-amz-security-token:${session.sessionToken}\n"
        val signedHeaders = "host;x-amz-date;x-amz-security-token"
        val canonicalRequest = listOf(
            "GET",
            canonicalUri,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"
        val signingKey = signingKey(session.secretKey, dateStamp, region, service)
        val signature = hmacHex(signingKey, stringToSign)
        val authorization = "AWS4-HMAC-SHA256 Credential=${session.accessKey}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val fullUrl = if (query.isBlank()) baseUrl + path else baseUrl + path + "?" + query
        val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BlueConnect/3.2.1")
            setRequestProperty("Host", host)
            setRequestProperty("X-Amz-Date", amzDate)
            setRequestProperty("X-Amz-Security-Token", session.sessionToken)
            setRequestProperty("Authorization", authorization)
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching {
                val json = JSONObject(body)
                json.optString("errorMessage").ifBlank { json.optString("message") }
            }.getOrNull().orEmpty()
            throw IllegalStateException("HTTP $code${if (detail.isNotBlank()) ": $detail" else ""}")
        }
        return body
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String =
        hmac(key, data).joinToString("") { "%02x".format(it) }

    private fun signingKey(secret: String, date: String, region: String, service: String): ByteArray {
        val kDate = hmac(("AWS4" + secret).toByteArray(StandardCharsets.UTF_8), date)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, service)
        return hmac(kService, "aws4_request")
    }
}
