@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.znova.original

import com.znova.engine.Flags
import com.znova.engine.Tags
import com.znova.engine.content.*
import com.znova.engine.core.*
import com.znova.engine.rules.*
import com.znova.engine.state.LifeState
import com.znova.engine.state.Tier

object Counters {
    val HEALED = CounterKey("healed")
}

/**
 * Meta progression: unlocks, trackers, achievements, legacy modifiers, focuses, and observer notes.
 * Separate module demonstrating extensible meta systems without modifying the core.
 */
object SampleMetaModule : ContentModule {
    override fun register(builder: ContentBuilder) {
        builder.unlock(UnlockDef(UnlockId("legacy_long_liver"), UnlockKind.LEGACY, "Long Liver"))
        builder.unlock(UnlockDef(UnlockId("legacy_debtor"), UnlockKind.LEGACY, "Debtor"))
        builder.unlock(UnlockDef(UnlockId("origin_unlucky"), UnlockKind.START, "Unlucky"))
        builder.unlock(UnlockDef(UnlockId("start_bohemia"), UnlockKind.START, "Bohemian Upbringing"))
        builder.unlock(UnlockDef(UnlockId("content_medicine"), UnlockKind.CONTENT, "Medicine"))
        builder.unlock(UnlockDef(UnlockId("mechanic_documents"), UnlockKind.MECHANIC, "Documents"))

        builder.tracker(TrackerDef(TrackerId("debt"), HasFlag(Flags.IN_DEBT)))
        builder.tracker(TrackerDef(TrackerId("jobless"), AgeBetween(18, 65) and !HasFlag(Flags.EMPLOYED)))

        builder.achievement(
            AchievementDef(
                AchievementId("debt"),
                "Debt",
                false,
                TrackerAtLeast(TrackerId("debt"), best = 3),
                unlocks = setOf(UnlockId("legacy_debtor")),
            ),
        )
        builder.achievement(
            AchievementDef(
                AchievementId("idler"),
                "Doing Nothing",
                true,
                TrackerAtLeast(TrackerId("jobless"), total = 8),
                unlocks = setOf(UnlockId("start_bohemia")),
                hint = "There is a gap in the archive: almost no record of employment.",
                hintWhen = TrackerAtLeast(TrackerId("jobless"), total = 4),
            ),
        )
        builder.achievement(
            AchievementDef(
                AchievementId("white_coat"),
                "White Coat",
                false,
                CounterAtLeast(Counters.HEALED, 3),
                unlocks = setOf(UnlockId("content_medicine")),
            ),
        )

        builder.origin(
            OriginDef(
                OriginId("bohemia"),
                "Bohemian",
                "Your parents never worked a day in their lives.",
                effects = listOf(ChangeSkill(Skills.EXPRESSION, 2), AddMoney(-20)),
                requiresUnlock = UnlockId("start_bohemia"),
            ),
        )

        builder.event(
            EventDef(
                EventId("clinic_shift"),
                Tier.MEDIUM,
                "The clinic is short-staffed.",
                conditions = listOf(AgeBetween(18, 65)),
                topics = setOf("medicine"),
                requiresUnlock = UnlockId("content_medicine"),
                choices =
                    listOf(
                        ChoiceDef(
                            ChoiceId("help"),
                            "Fill In for the Doctor",
                            check = SkillCheck(Skills.INTELLECT, 8),
                            onSuccess =
                                listOf(
                                    AddCounter(Counters.HEALED),
                                    ChangeSkill(Skills.INTELLECT, 1),
                                ),
                            onFailure = listOf(ChangeStat(Stats.SPIRIT, -6)),
                            resultText = "You saved someone's life.",
                            failText = "You couldn't save them.",
                        ),
                        ChoiceDef(
                            ChoiceId("pass"),
                            "Walk Away",
                            resultText = "You walked past.",
                        ),
                    ),
            ),
        )

        builder.event(
            EventDef(
                EventId("patron_favor"),
                Tier.MEDIUM,
                "{npc.patron} asks you for a favor.",
                weight = 0.0,
                choices =
                    listOf(
                        ChoiceDef(
                            ChoiceId("repay"),
                            "Do Them a Favor",
                            onSuccess =
                                listOf(
                                    ChangeAttitude(RoleKey("patron"), 2),
                                    ShiftView(Views.ORDER, -1),
                                ),
                            resultText = "A favor for a favor.",
                        ),
                        ChoiceDef(
                            ChoiceId("refuse"),
                            "Refuse",
                            onSuccess = listOf(ChangeAttitude(RoleKey("patron"), -4)),
                            resultText = "The connection is severed.",
                        ),
                    ),
            ),
        )

        builder.legacy(
            LegacyDef(
                LegacyId("debtor"),
                "Debtor",
                "Start in debt, but gain access to underground loans.",
                onStart =
                    listOf(
                        AddMoney(-200),
                        SetFlag(Flags.IN_DEBT),
                        SetFlag(Flags.KNOWS_SMUGGLER),
                    ),
                requiresUnlock = UnlockId("legacy_debtor"),
            ),
        )

        builder.legacy(
            LegacyDef(
                LegacyId("weak_heart"),
                "Weak Heart",
                "Less Body, more medical events.",
                onStart = listOf(ChangeStat(Stats.BODY, -15)),
                gameModifiers =
                    listOf(
                        object : GameModifier {
                            override fun eventWeight(
                                state: LifeState,
                                event: EventDef,
                                weight: Double,
                            ) = if (event.id.id == "illness") weight * 2.0 else weight
                        },
                    ),
            ),
        )

        builder.legacy(
            LegacyDef(
                LegacyId("fathers_ties"),
                "Father's Connections",
                "An influential acquaintance, but he'll expect a favor in return.",
                onStart =
                    listOf(
                        AddNpc(RoleKey("patron"), 3, name = "Patron"),
                        Schedule(EventId("patron_favor"), 6),
                    ),
            ),
        )

        builder.legacy(
            LegacyDef(
                LegacyId("observer"),
                "Observer",
                "You can see hidden options, but cannot choose them.",
                gameModifiers =
                    listOf(
                        object : GameModifier {
                            override fun choiceVisible(
                                state: LifeState,
                                choice: ChoiceDef,
                                visible: Boolean,
                            ) = true

                            override fun choiceEnabled(
                                state: LifeState,
                                choice: ChoiceDef,
                                enabled: Boolean,
                            ) = enabled && choice.visibleWhen.all { it.test(state) }
                        },
                    ),
            ),
        )

        builder.legacy(
            LegacyDef(
                LegacyId("long_liver"),
                "Long Liver",
                "Body deteriorates twice as slowly.",
                gameModifiers =
                    listOf(
                        object : GameModifier {
                            override fun statDelta(
                                state: LifeState,
                                stat: StatKey,
                                delta: Int,
                            ) = if (stat == Stats.BODY && delta < 0) delta / 2 else delta
                        },
                    ),
                requiresUnlock = UnlockId("legacy_long_liver"),
            ),
        )

        builder.focus(
            FocusDef(
                FocusId("politics"),
                "Politics",
                "Protests and informants appear more frequently.",
                mapOf("politics" to 3.0),
            ),
        )

        builder.focus(
            FocusDef(
                FocusId("medicine"),
                "Medicine",
                "Illnesses and clinic events appear more frequently.",
                mapOf("medicine" to 3.0),
                requiresUnlock = UnlockId("content_medicine"),
            ),
        )

        builder.observerNote(
            ObserverNoteDef(
                "deviation",
                "Deviation recorded.",
                listOf(HasTag(Tags.REBEL)),
            ),
        )

        builder.observerNote(
            ObserverNoteDef(
                "least_resistance",
                "The subject has once again chosen the path of least resistance.",
                listOf(ViewBetween(Views.AMBITION, max = 0)),
            ),
        )
    }
}
