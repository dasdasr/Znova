package com.znova.original

import com.znova.engine.content.ActivityDef
import com.znova.engine.content.ChoiceDef
import com.znova.engine.content.ContentBuilder
import com.znova.engine.content.ContentModule
import com.znova.engine.content.EventDef
import com.znova.engine.content.NamePool
import com.znova.engine.content.OriginDef
import com.znova.engine.content.TitleDef
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.EventId
import com.znova.engine.core.OriginId
import com.znova.engine.core.TagKey
import com.znova.engine.rules.AddMoney
import com.znova.engine.rules.AgeBetween
import com.znova.engine.rules.ChangeSkill
import com.znova.engine.rules.ChangeStat
import com.znova.engine.rules.MoneyBetween
import com.znova.engine.rules.RaiseTag
import com.znova.engine.rules.SkillCheck
import com.znova.engine.state.Tier

/** Development-only content for testing engine features. */
object BaseContent : ContentModule {

    override fun register(builder: ContentBuilder) {
        registerOrigins(builder)
        registerEvents(builder)
        registerActivities(builder)
        registerTitles(builder)
    }

    // Required by content validation until real origins are registered.
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
                        cooldownChapters = 0, // Allow test events to appear in consecutive chapters.
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
        builder.title(
            TitleDef(
                "The Long-Liver",
                priority = 10,
                conditions = listOf(AgeBetween(minYears = 60))
            )
        )
        builder.title(
            TitleDef(
                "Gone Too Soon",
                priority = 5,
                conditions = listOf(AgeBetween(maxYears = 30))
            )
        )
    }
}
