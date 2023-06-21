package types

data class City(
    val id: Int,
    val name: String,
    val admin: String,
    val latitude: Double,
    val longitude: Double
){
    override fun toString() = name
}
