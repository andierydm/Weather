package util

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.RoundingMode
import java.nio.charset.Charset
import java.text.DecimalFormat
import kotlin.io.path.Path
import kotlin.io.path.absolute

@Serializable
data class CoordinateInformation(
    @SerialName("lat")
    val latitude: Double,
    @SerialName("lon")
    val longitude: Double,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("address")
    val coordinateAddress: CoordinateAddress
)

@Serializable
data class CoordinateAddress @OptIn(ExperimentalSerializationApi::class) constructor(
    @SerialName("city")
    @EncodeDefault
    val city: String = "",
    @SerialName("country")
    val country: String,
    @SerialName("state")
    @EncodeDefault
    val state: String = "",
    @SerialName("country_code")
    val countryCode: String
)

@Serializable
data class CurrentTime(
    @SerialName("year")
    val year: Int,
    @SerialName("month")
    val month: Int,
    @SerialName("day")
    val day: Int,
    @SerialName("hour")
    val hour: Int,
    @SerialName("minute")
    val minute: Int,
    @SerialName("seconds")
    val seconds: Int,
    @SerialName("milliSeconds")
    val milliSeconds: Int
)

fun celsiusToFahrenheit(celsius: Double): Double {
    val formatter = DecimalFormat(".##")
    formatter.roundingMode = RoundingMode.HALF_EVEN
    return formatter.format(celsius * 9 / 5 + 32).toDouble()
}

fun getCoordinateInformation(latitude: Double, longitude: Double): Either<CoordinateInformation, Throwable> {
    try {
        val baseUrl = "https://nominatim.openstreetmap.org/reverse?"
        val location = "lat=$latitude&lon=$longitude"
        val url = "${baseUrl}$location&format=json"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept-Language", "en-US,en;q=0.5")
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful)
            return Left(IllegalStateException("Could not retrieve coordinates name. $response.message"))

        val json = Json { ignoreUnknownKeys = true }
        val jsonResponse = response.body.bytes().toString(Charset.forName("utf-8"))
        val information = json.decodeFromString<CoordinateInformation>(jsonResponse)

        return Right(information)
    } catch (ex: Throwable) {
        ex.printStackTrace()
        return Left(IllegalStateException("Could not retrieve coordinates name", ex))
    }
}

fun getCurrentTime(latitude: Double, longitude: Double): Either<CurrentTime, Throwable> {
    try {
        val json = Json { ignoreUnknownKeys = true }

        val lat = "latitude=$latitude"
        val long = "&longitude=$longitude"
        val url = "https://timeapi.io/api/Time/current/coordinate?${lat}${long}"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful)
            return Left(IllegalStateException("Could not determinate time. ${response.message}"))

        val jsonResponse = response.body.bytes().toString(Charset.forName("utf-8"))
        val currentTime = json.decodeFromString<CurrentTime>(jsonResponse)

        return Right(currentTime)
    } catch (ex: Throwable) {
        return Left(IllegalStateException("Could not determinate time", ex))
    }
}

fun getCountryFlagImg(countryCode: String, size: Int = 64): Either<ByteArray, Throwable> {
    try {
        val url = "https://flagsapi.com/$countryCode/flat/$size.png"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful)
            return Left(IllegalStateException("Could not get country flag image. ${response.message}"))

        val flagImage = response.body.bytes()

        return Right(flagImage)
    } catch (ex: Throwable) {
        return Left(IllegalStateException("Could not get country flag image.", ex))
    }
}

fun buildChain(
    vararg values: String,
    separator: String = ",",
    processEmpty: Boolean = false
): String {
    val sb = StringBuilder()

    values.forEachIndexed { index, s ->

        val addSeparator = if (s.isBlank()) {
            if (processEmpty) {
                sb.append(s)
                true
            } else {
                false
            }
        } else {
            sb.append(s)
            true
        }

        if (index + 1 < values.size && addSeparator) sb.append("$separator ")
    }

    return sb.toString()
}

fun buildAddress(coordinateInformation: CoordinateInformation): String {
    val coordinateAddress = coordinateInformation.coordinateAddress
    return if (coordinateAddress.city.isBlank() && coordinateAddress.state.isBlank())
        coordinateInformation.displayName
    else
        buildChain(
            coordinateAddress.city,
            coordinateAddress.state,
            coordinateAddress.country
        )
}

fun getAbsolutePath() = try {
    Path("").absolute().toString()
} catch (ex: Throwable) {
    null
}