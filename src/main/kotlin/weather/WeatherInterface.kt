package weather

import util.Either

interface WeatherInterface {
    fun isConcise(): Boolean

    fun invalidate()

    fun update(
        latitude: Double,
        longitude: Double
    ): Either<Boolean, Throwable>

    fun getTemperature(unit: TemperatureUnit = TemperatureUnit.CELSIUS): Double

    fun getWeatherDescription(): String

    fun getLatitude(): Double

    fun getLongitude(): Double
}