package com.znova.engine

import com.znova.engine.content.AchievementDef
import com.znova.engine.content.ActivityDef
import com.znova.engine.content.ChoiceDef
import com.znova.engine.content.ContentBuilder
import com.znova.engine.content.ContentModule
import com.znova.engine.content.EventDef
import com.znova.engine.content.LegacyDef
import com.znova.engine.content.NamePool
import com.znova.engine.content.OriginDef
import com.znova.engine.content.PlaceDef
import com.znova.engine.content.ThoughtDef
import com.znova.engine.content.TitleDef
import com.znova.engine.content.WeightRule
import com.znova.engine.core.AchievementId
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.EventId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.LawKey
import com.znova.engine.core.LegacyId
import com.znova.engine.core.OriginId
import com.znova.engine.core.PlaceId
import com.znova.engine.core.RoleKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.TagKey
import com.znova.engine.core.UnlockId
import com.znova.engine.rules.AddMoney
import com.znova.engine.rules.AddNpc
import com.znova.engine.rules.AgeBetween
import com.znova.engine.rules.ChangeAttitude
import com.znova.engine.rules.ChangeSkill
import com.znova.engine.rules.ChangeStat
import com.znova.engine.rules.ClearFlag
import com.znova.engine.rules.EndLife
import com.znova.engine.rules.EndedBy
import com.znova.engine.rules.GameModifier
import com.znova.engine.rules.HasFlag
import com.znova.engine.rules.HasNpc
import com.znova.engine.rules.HasTag
import com.znova.engine.rules.LawActive
import com.znova.engine.rules.MoneyBetween
import com.znova.engine.rules.MoveTo
import com.znova.engine.rules.RaiseTag
import com.znova.engine.rules.Schedule
import com.znova.engine.rules.SetFlag
import com.znova.engine.rules.ShiftView
import com.znova.engine.rules.SkillCheck
import com.znova.engine.rules.ViewBetween
import com.znova.engine.rules.When
import com.znova.engine.rules.not
import com.znova.engine.rules.or
import com.znova.engine.state.LifeState
import com.znova.engine.state.Tier
import com.znova.original.Skills
import com.znova.original.Stats
import com.znova.original.Views

object Laws {
    val CLOSED_BORDERS = LawKey("closed_borders")
    val CENSORSHIP = LawKey("censorship")
}

object Flags {
    val FIRED = FlagKey("fired")
    val IN_DEBT = FlagKey("in_debt")
    val EMPLOYED = FlagKey("employed")
    val KNOWS_SMUGGLER = FlagKey("knows_smuggler")
    val THOUGHT_LEAVE = FlagKey("thought.leave")
    val INFORMER = FlagKey("informer")
}

object Roles {
    val FRIEND = RoleKey("friend")
    val NEIGHBOR = RoleKey("neighbor")
    val SMUGGLER = RoleKey("smuggler")
}

object Tags {
    val REBEL = TagKey("rebel")
    val DRINKER = TagKey("drinker")
    val CAREGIVER = TagKey("caregiver")
}

private fun id(s: String) = EventId(s)

private fun c(s: String) = ChoiceId(s)

object SampleModule : ContentModule {
    override fun register(builder: ContentBuilder) {
        builder.place(
            PlaceDef(
                id = PlaceId("tsentral"),
                kind = "country",
                name = "Central Republic",
                startable = true,
                laws = setOf(Laws.CLOSED_BORDERS, Laws.CENSORSHIP),
                names =
                    NamePool(
                        listOf("Jan", "Mara", "Zoya", "Tikhon"),
                        listOf("Volsky", "Krein", "Lis"),
                    ),
            ),
        )

        builder.place(
            PlaceDef(
                id = PlaceId("pribrezhye"),
                kind = "country",
                name = "Coastal Union",
                startable = true,
                laws = emptySet(),
                names =
                    NamePool(
                        listOf("Ada", "Leo", "Nina"),
                        listOf("Pry", "Grom"),
                    ),
            ),
        )

        builder.origin(
            OriginDef(
                OriginId("workers"),
                "Working-Class Family",
                "",
                effects = listOf(AddMoney(50)),
            ),
        )
        builder.origin(
            OriginDef(
                OriginId("clerks"),
                "Clerical Family",
                "",
                effects = listOf(AddMoney(200), ChangeSkill(Skills.INTELLECT, 1)),
            ),
        )

        builder.legacy(
            LegacyDef(
                LegacyId("nervous_mind"),
                "Nervous Mind",
                "+Intellect, Spirit deteriorates faster",
                onStart = listOf(ChangeSkill(Skills.INTELLECT, 2)),
                gameModifiers =
                    listOf(
                        object : GameModifier {
                            override fun statDelta(
                                state: LifeState,
                                stat: StatKey,
                                delta: Int,
                            ) = if (stat == Stats.SPIRIT && delta < 0) delta * 2 else delta
                        },
                    ),
            ),
        )

        builder.upkeep(When(HasFlag(Flags.EMPLOYED), AddMoney(60)))
        builder.upkeep(When(HasFlag(Flags.IN_DEBT), ChangeStat(Stats.SPIRIT, -4)))

        builder.activity(
            ActivityDef(
                ActivityId("work_hard"),
                "Work Overtime",
                "work",
                conditions = listOf(AgeBetween(16, 70), HasFlag(Flags.EMPLOYED)),
                onSuccess =
                    listOf(
                        AddMoney(80),
                        ChangeStat(Stats.BODY, -3),
                        ShiftView(Views.AMBITION, 1),
                    ),
                resultText = "You worked overtime.",
            ),
        )

        builder.activity(
            ActivityDef(
                ActivityId("find_job"),
                "Look for a Job",
                "work",
                conditions = listOf(AgeBetween(16, 70), !HasFlag(Flags.EMPLOYED)),
                check = SkillCheck(Skills.RAPPORT, 8),
                onSuccess = listOf(SetFlag(Flags.EMPLOYED), ClearFlag(Flags.FIRED)),
                onFailure = listOf(ChangeStat(Stats.SPIRIT, -5)),
                resultText = "You found a job.",
                failText = "Everywhere turned you down.",
            ),
        )

        builder.activity(
            ActivityDef(
                ActivityId("study"),
                "Study",
                "study",
                conditions = listOf(AgeBetween(6, 45)),
                onSuccess = listOf(ChangeSkill(Skills.INTELLECT, 1)),
                resultText = "You learned something.",
            ),
        )

        builder.activity(
            ActivityDef(
                ActivityId("rest"),
                "Rest",
                "rest",
                onSuccess =
                    listOf(
                        ChangeStat(Stats.SPIRIT, 6),
                        ChangeStat(Stats.BODY, 3),
                    ),
            ),
        )

        builder.activity(
            ActivityDef(
                ActivityId("emigrate"),
                "Leave the Country",
                "move",
                conditions =
                    listOf(
                        AgeBetween(18, 60),
                        HasFlag(Flags.THOUGHT_LEAVE),
                        !LawActive(Laws.CLOSED_BORDERS) or HasFlag(Flags.KNOWS_SMUGGLER),
                    ),
                moneyCost = 150,
                check = SkillCheck(Skills.ADAPTABILITY, 8),
                onSuccess =
                    listOf(
                        MoveTo(PlaceId("pribrezhye")),
                        ClearFlag(Flags.THOUGHT_LEAVE),
                    ),
                onFailure = listOf(ChangeStat(Stats.BODY, -10)),
                resultText = "You made it out.",
                failText = "The border guards turned you away.",
                resultTier = Tier.MAJOR,
            ),
        )

        builder.thought(
            ThoughtDef(
                "leave",
                Skills.ADAPTABILITY,
                "Inner Voice: \"It's getting cramped here. Maybe it's time to leave?\"",
                conditions =
                    listOf(
                        AgeBetween(18, 60),
                        ViewBetween(Views.KINSHIP, max = 0),
                    ),
                effects = listOf(SetFlag(Flags.THOUGHT_LEAVE)),
            ),
        )

        builder.thought(
            ThoughtDef(
                "calm",
                Skills.AWARENESS,
                "Inner Voice: \"Quiet. Too quiet.\"",
            ),
        )

        builder.event(
            EventDef(
                id("fired"),
                Tier.MAJOR,
                "{name}, you've been laid off.",
                conditions = listOf(HasFlag(Flags.EMPLOYED)),
                onTrigger =
                    listOf(
                        ClearFlag(Flags.EMPLOYED),
                        SetFlag(Flags.FIRED),
                        Schedule(id("debt_collector"), 2),
                    ),
                weightRules = listOf(WeightRule(HasTag(Tags.REBEL), 3.0)),
                maxPerLife = 2,
                group = "work",
            ),
        )

        builder.event(
            EventDef(
                id("debt_collector"),
                Tier.MEDIUM,
                "Someone has come to collect your debt.",
                weight = 0.0,
                group = "money",
                choices =
                    listOf(
                        ChoiceDef(
                            c("pay"),
                            "Pay",
                            enabledWhen = listOf(MoneyBetween(min = 100)),
                            lockedHint = "Not enough money",
                            onSuccess = listOf(AddMoney(-100)),
                            resultText = "You paid off your debt.",
                        ),
                        ChoiceDef(
                            c("run"),
                            "Go into Hiding",
                            onSuccess =
                                listOf(
                                    SetFlag(Flags.IN_DEBT),
                                    ShiftView(Views.ORDER, -1),
                                ),
                            resultText = "Now you're in debt.",
                        ),
                        ChoiceDef(
                            c("smuggler"),
                            "Borrow from a Smuggler",
                            visibleWhen = listOf(HasFlag(Flags.KNOWS_SMUGGLER)),
                            onSuccess =
                                listOf(
                                    AddMoney(300),
                                    SetFlag(Flags.IN_DEBT),
                                ),
                            resultText = "The money smells of fish.",
                        ),
                    ),
            ),
        )

        builder.event(
            EventDef(
                id("neighbor_quarrel"),
                Tier.MEDIUM,
                "{name} had an argument with {npc.neighbor}.",
                conditions =
                    listOf(
                        AgeBetween(14, 80),
                        HasNpc(Roles.NEIGHBOR),
                    ),
                group = "social",
                choices =
                    listOf(
                        ChoiceDef(
                            c("apologize"),
                            "Apologize",
                            onSuccess =
                                listOf(
                                    ChangeAttitude(Roles.NEIGHBOR, 2),
                                    ShiftView(Views.HARDNESS, -1),
                                ),
                            resultText = "You made peace.",
                        ),
                        ChoiceDef(
                            c("report"),
                            "Report Them",
                            check = SkillCheck(Skills.INFLUENCE, 7),
                            onSuccess =
                                listOf(
                                    SetFlag(Flags.INFORMER),
                                    ShiftView(Views.ORDER, 2),
                                    RaiseTag(Tags.REBEL, -1),
                                ),
                            onFailure = listOf(ChangeAttitude(Roles.NEIGHBOR, -3)),
                            resultText = "Your neighbor was taken away.",
                            failText = "Nobody believed you.",
                        ),
                    ),
            ),
        )

        builder.event(
            EventDef(
                id("new_neighbor"),
                Tier.MINOR,
                "A new neighbor has moved in.",
                conditions = listOf(!HasNpc(Roles.NEIGHBOR)),
                onTrigger = listOf(AddNpc(Roles.NEIGHBOR, 0)),
            ),
        )

        builder.event(
            EventDef(
                id("meet_smuggler"),
                Tier.MEDIUM,
                "Someone joins you at the bar.",
                conditions =
                    listOf(
                        AgeBetween(18, 60),
                        HasTag(Tags.REBEL),
                    ),
                requiresUnlock = null,
                onTrigger =
                    listOf(
                        AddNpc(Roles.SMUGGLER, 1),
                        SetFlag(Flags.KNOWS_SMUGGLER),
                    ),
                cooldownChapters = 100,
            ),
        )

        builder.event(
            EventDef(
                id("protest"),
                Tier.MAJOR,
                "A crowd is gathering in the square.",
                conditions =
                    listOf(
                        AgeBetween(16, 60),
                        LawActive(Laws.CENSORSHIP),
                    ),
                topics = setOf("politics"),
                weightRules =
                    listOf(
                        WeightRule(ViewBetween(Views.ORDER, max = -2), 4.0),
                    ),
                choices =
                    listOf(
                        ChoiceDef(
                            c("join"),
                            "Join Them",
                            onSuccess =
                                listOf(
                                    RaiseTag(Tags.REBEL),
                                    ShiftView(Views.ORDER, -2),
                                    ChangeStat(Stats.BODY, -8),
                                ),
                            resultText = "They won't forget your face.",
                        ),
                        ChoiceDef(
                            c("stay"),
                            "Stay at Home",
                            onSuccess = listOf(ShiftView(Views.ORDER, 1)),
                            resultText = "You waited it out.",
                        ),
                    ),
            ),
        )

        builder.event(
            EventDef(
                id("bar_night"),
                Tier.MINOR,
                "A Night at the Bar.",
                conditions = listOf(AgeBetween(18, 80)),
                onTrigger =
                    listOf(
                        RaiseTag(Tags.DRINKER),
                        ChangeStat(Stats.BODY, -2),
                    ),
            ),
        )

        builder.event(
            EventDef(
                id("illness"),
                Tier.MEDIUM,
                "You've been struck down by illness.",
                weightRules =
                    listOf(
                        WeightRule(HasTag(Tags.DRINKER, 2), 3.0),
                    ),
                choices =
                    listOf(
                        ChoiceDef(
                            c("treat"),
                            "Get Treatment",
                            enabledWhen = listOf(MoneyBetween(min = 50)),
                            lockedHint = "Not enough money",
                            onSuccess =
                                listOf(
                                    AddMoney(-50),
                                    ChangeStat(Stats.BODY, -3),
                                ),
                            resultText = "You pulled through.",
                        ),
                        ChoiceDef(
                            c("endure"),
                            "Tough It Out",
                            check = SkillCheck(Skills.PHYSICALITY, 8),
                            onSuccess = listOf(ChangeStat(Stats.BODY, -6)),
                            onFailure = listOf(ChangeStat(Stats.BODY, -35)),
                            resultText = "You made it through.",
                            failText = "Your body gave out.",
                        ),
                    ),
            ),
        )

        builder.event(
            EventDef(
                id("fatal_accident"),
                Tier.MAJOR,
                "A fatal accident.",
                weight = 0.15,
                conditions = listOf(AgeBetween(18, 80)),
                onTrigger = listOf(EndLife("accident")),
            ),
        )

        builder.title(
            TitleDef(
                "Underground Radio Operator",
                10,
                listOf(
                    HasTag(Tags.REBEL),
                    HasFlag(Flags.KNOWS_SMUGGLER),
                ),
            ),
        )

        builder.title(
            TitleDef(
                "Informer",
                8,
                listOf(HasFlag(Flags.INFORMER)),
            ),
        )

        builder.title(
            TitleDef(
                "Quiet Accountant",
                1,
                listOf(
                    !HasTag(Tags.REBEL),
                    HasFlag(Flags.EMPLOYED),
                ),
            ),
        )

        builder.achievement(
            AchievementDef(
                AchievementId("live_to_80"),
                "Live to 80",
                false,
                AgeBetween(80),
                unlocks = setOf(UnlockId("legacy_long_liver")),
            ),
        )

        builder.achievement(
            AchievementDef(
                AchievementId("accident"),
                "An Absurd Death",
                true,
                EndedBy("accident"),
                unlocks = setOf(UnlockId("origin_unlucky")),
            ),
        )
    }
}
