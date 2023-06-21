package weather

import util.Either
import util.Left
import util.Right
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import util.celsiusToFahrenheit
import java.lang.StringBuilder
import java.nio.charset.Charset

class OpenMeteoWeather : WeatherInterface {
    private val lock = Any()
    private var isConcise = false
    private val client = OkHttpClient()
    private var weather: WeatherInformation? = null
    private val json = Json { ignoreUnknownKeys = true }

    override fun isConcise() = synchronized(lock) { isConcise }

    override fun invalidate() = synchronized(lock) {
        isConcise = false
        weather = null
    }

    override fun getTemperature(unit: TemperatureUnit): Double = synchronized(lock) {
        val w = weather ?: return 0.0

        return when (unit) {
            TemperatureUnit.CELSIUS ->
                w.currentWeather.temperature

            TemperatureUnit.FAHRENHEIT ->
                celsiusToFahrenheit(w.currentWeather.temperature)
        }
    }

    override fun update(
        latitude: Double,
        longitude: Double,
    ): Either<Boolean, Throwable> = synchronized(lock) {
        try {
            val url = QueryBuilder()
                .setCoordinates(latitude, longitude)
                .setCurrentWeather(true)
                .setTemperatureUnit(TemperatureUnit.CELSIUS)
                .build()
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                isConcise = false
                weather = null
                return Left(IllegalStateException("Could not retrieve weather. $response.message"))
            }

            val jsonResponse = response.body.bytes().toString(Charset.forName("utf-8"))
            val information = json.decodeFromString<WeatherInformation>(jsonResponse)

            isConcise = true
            weather = information

            return Right(true)
        } catch (ex: Throwable) {
            isConcise = false
            weather = null
            return Left(IllegalStateException("Could not retrieve weather", ex))
        }
    }

    override fun getWeatherDescription(): String = synchronized(lock) {
        val w = weather ?: return ""

        return when (w.currentWeather.weatherCode) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear, partly cloudy, and overcast"
            45, 48 -> "Fog and depositing rime fog"
            51, 53, 55 -> "Drizzle: Light, moderate, and dense intensity"
            56, 57 -> "Freezing Drizzle: Light and dense intensity"
            61, 63, 65 -> "Rain: Slight, moderate and heavy intensity"
            66, 67 -> "Freezing Rain: Light and heavy intensity"
            71, 73, 75 -> "Snow fall: Slight, moderate, and heavy intensity"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers: Slight, moderate, and violent"
            85, 86 -> "Snow showers slight and heavy"
            95 -> "Thunderstorm: Slight or moderate"
            96, 99 -> "Thunderstorm with slight and heavy hail"
            else -> "Unknown"
        }
    }

    override fun getLatitude(): Double = synchronized(lock) {
        val w = weather ?: return 0.0
        return w.latitude
    }

    override fun getLongitude(): Double = synchronized(lock) {
        val w = weather ?: return 0.0
        return w.longitude
    }

    @Serializable
    data class WeatherInformation(
        @SerialName("latitude")
        val latitude: Double,
        @SerialName("longitude")
        val longitude: Double,
        @SerialName("current_weather")
        val currentWeather: CurrentWeather
    )

    @Serializable
    data class CurrentWeather(
        @SerialName("temperature")
        val temperature: Double,
        @SerialName("windspeed")
        val windSpeed: Double,
        @SerialName("winddirection")
        val windDirection: Double,
        @SerialName("weathercode")
        val weatherCode: Int
    )

    class QueryBuilder {
        private var base = "https://api.open-meteo.com/v1/forecast?"
        private var latitude: Double = 0.0
        private var longitude: Double = 0.0
        private var currentWeather: Boolean = false
        private var temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS

        fun setCoordinates(latitude: Double, longitude: Double): QueryBuilder {
            this.latitude = latitude
            this.longitude = longitude
            return this
        }

        fun setCurrentWeather(currentWeather: Boolean): QueryBuilder {
            this.currentWeather = currentWeather
            return this
        }

        fun setTemperatureUnit(temperatureUnit: TemperatureUnit): QueryBuilder {
            this.temperatureUnit = temperatureUnit
            return this
        }

        fun build(): String {
            return StringBuilder(base).also {
                it.append("longitude=$longitude&latitude=$latitude")
                it.append("&current_weather=$currentWeather")
                it.append("&temperature_unit=${temperatureUnit.value}")
            }.toString()
        }
    }
}