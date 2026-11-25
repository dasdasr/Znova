package com.znova.content

import com.znova.engine.content.ActivityDef
import com.znova.engine.content.ChoiceDef
import com.znova.engine.content.ContentBuilder
import com.znova.engine.content.ContentModule
import com.znova.engine.content.CountryDef
import com.znova.engine.content.EventDef
import com.znova.engine.content.NamePool
import com.znova.engine.content.OriginDef
import com.znova.engine.content.TitleDef
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.CountryId
import com.znova.engine.core.EventId
import com.znova.engine.core.OriginId
import com.znova.engine.core.Skills
import com.znova.engine.core.Stats
import com.znova.engine.core.TagKey
import com.znova.engine.rules.AddMoney
import com.znova.engine.rules.AgeBetween
import com.znova.engine.rules.ChangeSkill
import com.znova.engine.rules.ChangeStat
import com.znova.engine.rules.MoneyBetween
import com.znova.engine.rules.RaiseTag
import com.znova.engine.rules.SkillCheck
import com.znova.engine.state.Tier

/**
 * Test content used during development.
 * Country IDs must match the country_id properties in the map GeoJSON.
 */
object BaseContent : ContentModule {

    override fun register(builder: ContentBuilder) {
        registerCountries(builder)
        registerOrigins(builder)
        registerEvents(builder)
        registerActivities(builder)
        registerTitles(builder)
    }

    private fun registerCountries(builder: ContentBuilder) {
        val names = NamePool(
            first = listOf("Alex", "Marek", "Anton", "Leon", "Victor"),
            last = listOf("Kovalski", "Novak", "Berger", "Miller", "Urban"),
        )

        val countries = listOf(
            CountryDef(
                id = CountryId("litoria"),
                name = "Litoria",
                laws = emptySet(),
                names = names,
                government = "Constitutional Monarchy",
                currencyCode = "LIT",
                currencyName = "Litorian Crown",
                description = "Industrial and financial center of Central Europe.",
            ),
            CountryDef(
                id = CountryId("yatvegia"),
                name = "Yatvegia",
                laws = emptySet(),
                names = names,
                government = "Federal Parliamentary Republic",
                currencyCode = "YAT",
                currencyName = "Yatvegian Mark",
                description = "A federation of regions with strong local identities.",
            ),
            CountryDef(
                id = CountryId("polessia"),
                name = "Polessia",
                laws = emptySet(),
                names = names,
                government = "Presidential Republic",
                currencyCode = "POL",
                currencyName = "Polessian Ruble",
                description = "An industrial and agricultural country of forests and plains.",
            ),
            CountryDef(
                id = CountryId("podlania"),
                name = "Podlania",
                laws = emptySet(),
                names = names,
                government = "Parliamentary Republic",
                currencyCode = "POD",
                currencyName = "Podlanian Zloty",
                description = "A commercially influential country with a developed banking sector.",
            ),
            CountryDef(
                id = CountryId("narevia"),
                name = "Narevia",
                laws = emptySet(),
                names = names,
                government = "Semi-Presidential Republic",
                currencyCode = "NAR",
                currencyName = "Narevian Dinar",
                description = "A transport hub with major railways and river ports.",
            ),
            CountryDef(
                id = CountryId("negovka"),
                name = "Negovka",
                laws = emptySet(),
                names = names,
                government = "Constitutional Principality",
                currencyCode = "NEG",
                currencyName = "Negovkan Franc",
                description = "A small principality known for private banks and old architecture.",
            ),
            CountryDef(
                id = CountryId("lusatia"),
                name = "Lusatia",
                laws = emptySet(),
                names = names,
                government = "Federal Republic",
                currencyCode = "LUS",
                currencyName = "Lusatian Mark",
                description = "An engineering and manufacturing center with strong regional identities.",
            ),
            CountryDef(
                id = CountryId("sudovica"),
                name = "Sudovica",
                laws = emptySet(),
                names = names,
                government = "Parliamentary Federation",
                currencyCode = "SUD",
                currencyName = "Sudovican Franc",
                description = "A federation built around regional compromise and local institutions.",
            ),
            CountryDef(
                id = CountryId("ostrozhia"),
                name = "Ostrozhia",
                laws = emptySet(),
                names = names,
                government = "Semi-Presidential Republic",
                currencyCode = "OST",
                currencyName = "Ostrozhian Koruna",
                description = "A large industrial country undergoing economic transformation.",
            ),
            CountryDef(
                id = CountryId("chernavia"),
                name = "Chernavia",
                laws = emptySet(),
                names = names,
                government = "Presidential Republic",
                currencyCode = "CHE",
                currencyName = "Chernavian Lev",
                description = "A southern country with agriculture, mining and trading cities.",
            ),
        )

        countries.forEach(builder::country)
    }

    // Temporary origins required by content validation.
    private fun registerOrigins(builder: ContentBuilder) {

        builder.origin(
            OriginDef(
                id = OriginId("urban_worker"),
                name = "Urban Worker",
                description = "You grew up among factories, railways and crowded apartment blocks.",
            ),
        )

        builder.origin(
            OriginDef(
                id = OriginId("rural_worker"),
                name = "Rural Worker",
                description = "You come from a small settlement where work and family define everyday life.",
            ),
        )

        builder.origin(
            OriginDef(
                id = OriginId("technical_student"),
                name = "Technical Student",
                description = "You studied engineering in a country that depends on its technical workforce.",
            ),
        )
    }

    private fun registerEvents(builder: ContentBuilder) {
        val skill = Skills.ALL.first()

        Tier.entries.forEach { tier ->
            repeat(3) { i ->
                val key = "${tier.name.lowercase()}_$i"
                builder.event(
                    EventDef(
                        id = EventId("test_$key"),
                        tier = tier,
                        text = "[$key] {name}, {age} y.o. in {country}. Something happens.",
                        cooldownChapters = 0, // Keep test events available in consecutive chapters.
                        choices = listOf(
                            ChoiceDef(
                                id = ChoiceId("risk"),
                                label = "Take a risk",
                                check = SkillCheck(skill, difficulty = 8),
                                resultText = "It paid off.",
                                failText = "It went badly.",
                                onSuccess = listOf(AddMoney(50), RaiseTag(TagKey("bold"))),
                                onFailure = listOf(ChangeStat(Stats.BODY, -1)),
                            ),
                            ChoiceDef(
                                id = ChoiceId("rich"),
                                label = "Pay your way out",
                                enabledWhen = listOf(MoneyBetween(min = 100)),
                                lockedHint = "Needs 100 money",
                                resultText = "Money solved it.",
                                onSuccess = listOf(AddMoney(-100), ChangeStat(Stats.BODY, 1)),
                            ),
                            ChoiceDef(
                                id = ChoiceId("calm"),
                                label = "Stay calm",
                                resultText = "You stayed calm.",
                                onSuccess = listOf(ChangeStat(Stats.BODY, 1)),
                            ),
                        ),
                    ),
                )
            }
        }
        builder.event(
            EventDef(
                id = EventId("test_plain"),
                tier = Tier.MINOR,
                text = "A quiet month passes.",
                cooldownChapters = 0,
            ),
        )
    }

    private fun registerActivities(builder: ContentBuilder) {
        builder.activity(
            ActivityDef(
                id = ActivityId("test_work"), label = "Work", category = "test",
                resultText = "You earned some money.",
                onSuccess = listOf(AddMoney(80)),
            ),
        )
        builder.activity(
            ActivityDef(
                id = ActivityId("test_rest"), label = "Rest", category = "test",
                resultText = "You rested.",
                onSuccess = listOf(ChangeStat(Stats.BODY, 1)),
            ),
        )
        builder.activity(
            ActivityDef(
                id = ActivityId("test_study"), label = "Study", category = "test",
                check = SkillCheck(Skills.ALL.first(), difficulty = 7),
                resultText = "You learned something.",
                failText = "Nothing stuck.",
                onSuccess = listOf(ChangeSkill(Skills.ALL.first(), 1)),
            ),
        )
        builder.activity(
            ActivityDef(
                id = ActivityId("test_course"), label = "Paid course (50)", category = "test",
                moneyCost = 50,
                resultText = "Worth the money.",
                onSuccess = listOf(ChangeSkill(Skills.ALL.first(), 1)),
            ),
        )
    }

    private fun registerTitles(builder: ContentBuilder) {
        builder.title(TitleDef("The Long-Liver", priority = 10, conditions = listOf(AgeBetween(minYears = 60))))
        builder.title(TitleDef("Gone Too Soon", priority = 5, conditions = listOf(AgeBetween(maxYears = 30))))
    }
}
