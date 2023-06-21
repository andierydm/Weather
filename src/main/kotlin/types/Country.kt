package types

data class Country(
    val id: Int,
    val name: String,
    val iso2: String,
    val cities: Array<City>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Country

        if (id != other.id) return false
        if (name != other.name) return false
        return cities.contentEquals(other.cities)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + cities.contentHashCode()
        return result
    }

    override fun toString() = name
}
