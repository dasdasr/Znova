package com.znova.content

import com.znova.engine.core.CountryId

enum class WorldCityType {
    CAPITAL,
    MAJOR_CITY,
    CITY,
}

/**
 * TEST DATA.
 *
 * City metadata is linked to geographic features through stable IDs.
 */
data class WorldCity(
    val id: String,
    val name: String,
    val countryId: CountryId,
    val type: WorldCityType,
)

object WorldCities {

    val all = listOf(
        WorldCity("kremenets", "Kremenets", CountryId("chernavia"), WorldCityType.MAJOR_CITY),
        WorldCity("krasava", "Krasava", CountryId("chernavia"), WorldCityType.CITY),

        WorldCity("veligrad", "Veligrad", CountryId("litoria"), WorldCityType.CAPITAL),
        WorldCity("valdren", "Valdren", CountryId("litoria"), WorldCityType.CITY),

        WorldCity("vislensk", "Vislensk", CountryId("lusatia"), WorldCityType.MAJOR_CITY),
        WorldCity("eisenau", "Eisenau", CountryId("lusatia"), WorldCityType.MAJOR_CITY),
        WorldCity("falken", "Falken", CountryId("lusatia"), WorldCityType.CITY),

        WorldCity("novoyork", "Novoyork", CountryId("narevia"), WorldCityType.CAPITAL),
        WorldCity("negovka", "Negovka", CountryId("negovka"), WorldCityType.CAPITAL),

        WorldCity("borsilay", "Borsilav", CountryId("ostrozhia"), WorldCityType.MAJOR_CITY),
        WorldCity("rudograd", "Rudograd", CountryId("ostrozhia"), WorldCityType.CITY),

        WorldCity("radomsk", "Radomsk", CountryId("podlania"), WorldCityType.MAJOR_CITY),
        WorldCity("brzegov", "Brzegov", CountryId("podlania"), WorldCityType.MAJOR_CITY),

        WorldCity("berestye", "Berestye", CountryId("polessia"), WorldCityType.MAJOR_CITY),

        WorldCity("odrava", "Odrava", CountryId("sudovica"), WorldCityType.MAJOR_CITY),
        WorldCity("zelenik", "Zelenik", CountryId("sudovica"), WorldCityType.CITY),
        WorldCity("vesava", "Vesava", CountryId("sudovica"), WorldCityType.CAPITAL),

        WorldCity("turov", "Turov", CountryId("yatvegia"), WorldCityType.MAJOR_CITY),
        WorldCity("varnava", "Varnava", CountryId("yatvegia"), WorldCityType.MAJOR_CITY),
    )

    val byId = all.associateBy { it.id }

    val byCountry = all.groupBy { it.countryId }
}
