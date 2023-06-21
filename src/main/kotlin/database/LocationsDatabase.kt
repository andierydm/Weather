package database

import types.City
import types.Country
import util.Either
import util.Left
import util.Right
import util.getAbsolutePath
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException

class LocationsDatabase {
    companion object {
        private fun getAllCities(connection: Connection, countryId: Int): Either<Array<City>, Throwable> {
            var preparedStatement: PreparedStatement? = null
            val query = """
                SELECT
                city.id,
                city.name,
                city.latitude,
                city.longitude,
                (
                	SELECT 
                	admin.name 
                	FROM admin 
                	WHERE id = city.admin_id
                ) AS admin
                FROM city
                WHERE city.country_id = ?
                ORDER BY name
            """.trimIndent()

            try {
                val ps = connection.prepareStatement(query)
                    ?: return Left(IllegalStateException("Could not get all cities: no result from cities"))

                preparedStatement = ps
                ps.setInt(1, countryId)

                val cities = mutableListOf<City>()
                val resultSet = ps.executeQuery()
                    ?: return Left(IllegalStateException("Could not get all cities: no result from cities"))

                resultSet.use {
                    while (resultSet.next()) {
                        val id = resultSet.getInt("id")
                        val name = resultSet.getString("name")
                        val latitude = resultSet.getDouble("latitude")
                        val longitude = resultSet.getDouble("longitude")
                        val admin = resultSet.getString("admin") ?: ""
                        val city = City(id, name, admin, latitude, longitude)

                        cities.add(city)
                    }
                }

                return Right(cities.toTypedArray())
            } catch (ex: SQLException) {
                return Left(IllegalStateException("Could not get all cities. ${ex.message}", ex))
            } finally {
                preparedStatement?.close()
            }
        }

        fun getAllCountries(): Either<Array<Country>, Throwable> {
            var connection: Connection? = null

            try {
                val conn = DriverManager.getConnection("jdbc:sqlite:./CitiesCoordinates.db")
                    ?: return Left(IllegalStateException("Failed to read coordinates database: could not open database connection"))

                connection = conn

                val preparedStatement = connection.prepareStatement("SELECT * FROM country ORDER BY name")
                    ?: return Left(IllegalStateException("Failed to read coordinates database: no result from country"))
                val resultSet = preparedStatement.executeQuery()
                    ?: return Left(IllegalStateException("Failed to read coordinates database: no result from country"))

                val countries = mutableListOf<Country>()

                resultSet.use {
                    while (it.next()) {
                        val id = it.getInt("id")
                        val name = it.getString("name")
                        val iso2 = it.getString("iso2")
                        val cities = when (val result = getAllCities(connection, id)) {
                            is Right -> result.value
                            is Left ->
                                return Left(
                                    IllegalStateException(
                                        "Could not read coordinates database: failed to retrieve a record",
                                        result.error
                                    )
                                )
                        }

                        countries.add(
                            Country(
                                id = id,
                                name = name,
                                iso2 = iso2,
                                cities = cities
                            )
                        )
                    }
                }

                return Right(countries.toTypedArray())
            } catch (ex: SQLException) {
                return Left(IllegalStateException("Could not read coordinates database. ${ex.message}", ex))
            } finally {
                connection?.close()
            }
        }
    }
}