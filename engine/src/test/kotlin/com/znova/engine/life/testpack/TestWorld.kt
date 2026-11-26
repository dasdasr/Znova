package com.znova.engine.life.testpack

import com.znova.engine.content.AchievementDef
import com.znova.engine.content.ActivityDef
import com.znova.engine.content.ChoiceDef
import com.znova.engine.content.ContentBuilder
import com.znova.engine.content.ContentModule
import com.znova.engine.content.ContentPack
import com.znova.engine.content.NamePool
import com.znova.engine.content.OriginDef
import com.znova.engine.content.PlaceDef
import com.znova.engine.content.TitleDef
import com.znova.engine.content.TrackerDef
import com.znova.engine.content.UnlockDef
import com.znova.engine.content.UnlockKind
import com.znova.engine.content.WeightRule
import com.znova.engine.core.AchievementId
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.EventId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.OriginId
import com.znova.engine.core.PlaceId
import com.znova.engine.core.RoleKey
import com.znova.engine.core.SkillKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.TagKey
import com.znova.engine.core.TrackerId
import com.znova.engine.core.UnlockId
import com.znova.engine.core.ViewKey
import com.znova.engine.life.ActivityDone
import com.znova.engine.life.ChangeAttitudeOf
import com.znova.engine.life.DepartNpc
import com.znova.engine.life.HasPursuit
import com.znova.engine.life.InteractKind
import com.znova.engine.life.InteractionDef
import com.znova.engine.life.LeaveRule
import com.znova.engine.life.LifeActivityDef
import com.znova.engine.life.LifeConfig
import com.znova.engine.life.LifeContent
import com.znova.engine.life.LifeContentBuilder
import com.znova.engine.life.LifeEnded
import com.znova.engine.life.LifeEventDef
import com.znova.engine.life.LifeModule
import com.znova.engine.life.LifeScenario
import com.znova.engine.life.LifeSimEngine
import com.znova.engine.life.LossRule
import com.znova.engine.life.MarkFlag
import com.znova.engine.life.MeetNpc
import com.znova.engine.life.MemoryTypes
import com.znova.engine.life.MissedPolicy
import com.znova.engine.life.NpcAct
import com.znova.engine.life.NpcAttitudeBetween
import com.znova.engine.life.NpcPresent
import com.znova.engine.life.NpcReactionDef
import com.znova.engine.life.NpcRemembers
import com.znova.engine.life.OpenOpportunity
import com.znova.engine.life.OpenSpec
import com.znova.engine.life.OpportunityGen
import com.znova.engine.life.OpportunityOpen
import com.znova.engine.life.ParticipantReq
import com.znova.engine.life.Rarity
import com.znova.engine.life.Remember
import com.znova.engine.life.ScenarioMemory
import com.znova.engine.life.ScenarioPast
import com.znova.engine.life.ScenarioPerson
import com.znova.engine.life.ScheduleIn
import com.znova.engine.life.SlotBand
import com.znova.engine.life.TierRates
import com.znova.engine.life.UnpaidPolicy
import com.znova.engine.meta.LifeSetup
import com.znova.engine.rules.AddMoney
import com.znova.engine.rules.AgeBetween
import com.znova.engine.rules.Chance
import com.znova.engine.rules.ChangeSkill
import com.znova.engine.rules.ChangeStat
import com.znova.engine.rules.ClearFlag
import com.znova.engine.rules.Condition
import com.znova.engine.rules.ConditionalBonus
import com.znova.engine.rules.DropTag
import com.znova.engine.rules.Effect
import com.znova.engine.rules.HasFlag
import com.znova.engine.rules.HasNpc
import com.znova.engine.rules.HasTag
import com.znova.engine.rules.MoneyBetween
import com.znova.engine.rules.RaiseTag
import com.znova.engine.rules.ShiftView
import com.znova.engine.rules.SkillAtLeast
import com.znova.engine.rules.SkillCheck
import com.znova.engine.rules.When
import com.znova.engine.rules.not
import com.znova.engine.sim.EngineConfig
import com.znova.engine.sim.GompertzMortality
import com.znova.engine.sim.MortalityModel
import com.znova.engine.sim.Pacing
import com.znova.engine.sim.PacingBand
import com.znova.engine.sim.TierBudget
import com.znova.engine.state.Tier
import com.znova.engine.world.SkillDef
import com.znova.engine.world.StatDef
import com.znova.engine.world.ViewDef
import com.znova.engine.world.WorldSchema

object Stats {
    val BODY = StatKey("body")
    val SPIRIT = StatKey("spirit")
}

object Skills {
    val INTELLECT = SkillKey("intellect")
    val RAPPORT = SkillKey("rapport")
    val CRAFT = SkillKey("craft")
    val GRIT = SkillKey("grit")
    val NERVE = SkillKey("nerve")
    val ALL = listOf(INTELLECT, RAPPORT, CRAFT, GRIT, NERVE)
}

object Views {
    val ORDER = ViewKey("order")
    val KINSHIP = ViewKey("kinship")
    val AMBITION = ViewKey("ambition")
    val ALL = listOf(ORDER, KINSHIP, AMBITION)
}

object Flag {
    val EMPLOYED = FlagKey("employed")
    val DEGREE = FlagKey("degree")
    val BAD_REP = FlagKey("bad_rep")
}

object Tag {
    val REBEL = TagKey("rebel")
}

object Role {
    val FRIEND = RoleKey("friend")
    val MENTOR = RoleKey("mentor")
    val FIXER = RoleKey("fixer")

    /** A slot, not an NPC role: "a friend taking part in this". */
    val ALLY = RoleKey("ally")
}

object Unlocks {
    val SCRAPBOOK = UnlockId("content_scrapbook")
    val ROAD = UnlockId("content_road")
    val DEGREE_START = UnlockId("start_degree")
}

object Act {
    val JOB_HUNT = ActivityId("job_hunt")
    val JOB_FACTORY = ActivityId("job_factory")
    val JOB_SHOP = ActivityId("job_shop")
    val JOB_ENGINEER = ActivityId("job_engineer")
    val JOB_WORKSHOP = ActivityId("job_workshop")
    val STUDY_EVENING = ActivityId("study_evening")
    val PREP_EXAM = ActivityId("prep_exam")
    val MENTOR_LESSONS = ActivityId("mentor_lessons")
    val APPRENTICESHIP = ActivityId("apprenticeship")
    val TRAIN_BODY = ActivityId("train_body")
    val READ_LIBRARY = ActivityId("read_library")
    val VOLUNTEER = ActivityId("volunteer")
    val START_PROJECT = ActivityId("start_project")
    val REST_HOME = ActivityId("rest_home")
    val GARAGE_BAND = ActivityId("garage_band")
    val LOCAL_GIG = ActivityId("local_gig")
    val STUDIO_CONTRACT = ActivityId("studio_contract")
    val SMALL_SCAM = ActivityId("small_scam")
    val BIG_SCAM = ActivityId("big_scam")
    val ROAD_TRIP = ActivityId("road_trip")
}

object Evt {
    val EXAM = EventId("exam")
    val MEET_FIXER = EventId("meet_fixer")
    val POLICE = EventId("police_visit")
    val BETRAY = EventId("fixer_betrays")
    val SCOUT = EventId("scout_talent")
    val BOSS_NOTICES = EventId("boss_notices")
    val LAYOFF = EventId("layoff")
    val ILLNESS = EventId("illness")
    val QUARREL = EventId("friend_quarrel")
    val IN_NEED = EventId("friend_in_need")
    val WINDFALL = EventId("windfall")
    val STREET_FIGHT = EventId("street_fight")
    val RAINY = EventId("rainy_month")
    val INSPIRATION = EventId("inspiration")
    val HEADHUNTER = EventId("headhunter_call")
    val GOSSIP = EventId("rival_gossip")
    val MEMORY_LANE = EventId("memory_lane")
}

object TestWorld {
    val schema =
        WorldSchema(
            stats = listOf(StatDef(Stats.BODY, "Body"), StatDef(Stats.SPIRIT, "Spirit")),
            skills = Skills.ALL.map { SkillDef(it, it.id) },
            views = Views.ALL.map { ViewDef(it, it.id) },
            vitalStat = Stats.BODY,
            vitalDeathCause = "body_failure",
        )

    val pack: ContentPack = ContentBuilder("test-life", schema).install(TestContentModule).build()

    val content: LifeContent = LifeContentBuilder(pack).install(TestLifeModule).build()

    val baseConfig =
        EngineConfig(
            pacing = Pacing(listOf(PacingBand(Int.MAX_VALUE, 12, TierBudget(0, 0, 0), 1))),
            mortality = GompertzMortality(schema.vitalStat),
            defaultStartYears = 1990..1990,
        )

    val config =
        LifeConfig(
            base = baseConfig,
            slotBands = listOf(SlotBand(16, 2), SlotBand(65, 3), SlotBand(Int.MAX_VALUE, 2)),
        )

    /** No random events, offers, intents, NPC actions or mortality: tests drive everything by hand. */
    fun quiet(): LifeConfig =
        config.copy(
            eventRates = TierRates(0.0, 0.0, 0.0),
            opportunityChance = 0.0,
            intentChance = 0.0,
            maxNpcActionsPerMonth = 0,
            base = baseConfig.copy(mortality = MortalityModel { _, _ -> 0.0 }),
        )

    fun engine(cfg: LifeConfig = config) = LifeSimEngine(content, cfg)

    fun setup(
        seed: Long,
        age: Int? = null,
    ) = LifeSetup(seed = seed, scenario = "city_youth", startAgeYears = age)
}

object TestContentModule : ContentModule {
    override fun register(builder: ContentBuilder) {
        builder.place(
            PlaceDef(
                id = PlaceId("town"),
                kind = "country",
                name = "Town",
                startable = true,
                laws = emptySet(),
                names = NamePool(listOf("Anna", "Boris", "Clara", "Dmitri"), listOf("Ivanov", "Petrova", "Sidorov")),
                startYears = 1990..1990,
            ),
        )
        builder.origin(OriginDef(OriginId("plain"), "Plain", ""))
        builder.unlock(UnlockDef(Unlocks.SCRAPBOOK, UnlockKind.CONTENT, "Scrapbook"))
        builder.unlock(UnlockDef(Unlocks.ROAD, UnlockKind.CONTENT, "Road trips"))
        builder.unlock(UnlockDef(Unlocks.DEGREE_START, UnlockKind.START, "Start with a degree"))
        builder.achievement(
            AchievementDef(AchievementId("mortal"), "Mortal", false, LifeEnded, unlocks = setOf(Unlocks.SCRAPBOOK)),
        )
        builder.achievement(
            AchievementDef(
                AchievementId("bandmate"),
                "Bandmate",
                false,
                ActivityDone(Act.GARAGE_BAND),
                unlocks = setOf(Unlocks.ROAD),
            ),
        )
        builder.achievement(
            AchievementDef(
                AchievementId("engineer"),
                "Engineer",
                true,
                HasFlag(Flag.DEGREE),
                unlocks = setOf(Unlocks.DEGREE_START),
                hint = "Some exams change a life.",
                hintWhen = ActivityDone(Act.PREP_EXAM),
            ),
        )
        builder.title(TitleDef("The Bandmate", 5, listOf(ActivityDone(Act.GARAGE_BAND))))
        builder.title(TitleDef("The Engineer", 8, listOf(HasFlag(Flag.DEGREE))))
        builder.tracker(TrackerDef(TrackerId("broke"), MoneyBetween(max = 0)))
    }
}

private const val SOFT_CAP = 7

/** Basic training stops at level 7: a year of play can never reach the maximum. */
private fun grow(
    skill: SkillKey,
    p: Double,
): Effect = When(!SkillAtLeast(skill, SOFT_CAP), Chance(p, ChangeSkill(skill, 1)))

private fun gain(skill: SkillKey): Effect = When(!SkillAtLeast(skill, SOFT_CAP), ChangeSkill(skill, 1))

private fun req(
    slot: RoleKey,
    npcRole: RoleKey? = null,
    required: Boolean = false,
    minAttitude: Int = -10,
) = ParticipantReq(slot, npcRole, required, minAttitude)

private fun ch(
    id: String,
    label: String,
    ok: List<Effect> = emptyList(),
    fail: List<Effect> = emptyList(),
    check: SkillCheck? = null,
    enabled: List<Condition> = emptyList(),
    visible: List<Condition> = emptyList(),
    locked: String? = null,
    text: String = "",
    failText: String? = null,
) = ChoiceDef(ChoiceId(id), label, visible, enabled, locked, check, ok, fail, text, failText)

@Suppress("LongParameterList")
private fun activity(
    id: ActivityId,
    label: String,
    category: String,
    duration: Int?,
    initiative: Boolean = false,
    conditions: List<Condition> = emptyList(),
    monthlyCost: Long = 0,
    unpaid: UnpaidPolicy = UnpaidPolicy.PAUSE,
    check: SkillCheck? = null,
    onStart: List<Effect> = emptyList(),
    onMonth: List<Effect> = emptyList(),
    onSuccess: List<Effect> = emptyList(),
    onFailure: List<Effect> = emptyList(),
    continueWhen: List<Condition> = emptyList(),
    who: List<ParticipantReq> = emptyList(),
    opens: List<OpenSpec> = emptyList(),
    leave: LeaveRule = LeaveRule(keepPercent = 30, restartCooldownMonths = 3),
    tier: Tier = Tier.MINOR,
    text: String = "",
    failText: String? = null,
    unlock: UnlockId? = null,
    lost: LossRule = LossRule.CONTINUE,
    repeatable: Boolean = true,
    intentViews: Map<ViewKey, Double> = emptyMap(),
) = LifeActivityDef(
    base = ActivityDef(id, label, category, conditions, 1, 0, check, onSuccess, onFailure, text, failText, tier, unlock),
    durationMonths = duration,
    initiative = initiative,
    continueWhen = continueWhen,
    repeatable = repeatable,
    monthlyCost = monthlyCost,
    onUnpaid = unpaid,
    onStart = onStart,
    onMonth = onMonth,
    leave = leave,
    participants = who,
    onParticipantLost = lost,
    opens = opens,
    intentViews = intentViews,
)

object TestLifeModule : LifeModule {
    override fun register(builder: com.znova.engine.life.LifeContentBuilder) {
        work(builder)
        learning(builder)
        body(builder)
        band(builder)
        shady(builder)
        events(builder)
        people(builder)
        world(builder)
    }

    private fun jobLeave() = LeaveRule(effects = listOf(ClearFlag(Flag.EMPLOYED)), restartCooldownMonths = 3)

    private fun job(
        id: ActivityId,
        label: String,
        pay: Long,
        extra: List<Effect> = emptyList(),
        conditions: List<Condition> = emptyList(),
    ) = activity(
        id,
        label,
        "work",
        null,
        conditions = listOf(AgeBetween(16, 60), !HasFlag(Flag.EMPLOYED)) + conditions,
        onStart = listOf(MarkFlag(Flag.EMPLOYED)),
        onMonth = listOf(AddMoney(pay), Chance(0.04, ScheduleIn(Evt.BOSS_NOTICES, 1))) + extra,
        continueWhen = listOf(HasFlag(Flag.EMPLOYED), AgeBetween(0, 65)),
        leave = jobLeave(),
        intentViews = mapOf(Views.AMBITION to 0.2),
    )

    private fun work(b: LifeContentBuilder) {
        b.activity(
            activity(
                Act.JOB_HUNT, "Look for work", "work", 3,
                initiative = true,
                conditions = listOf(AgeBetween(16, 65), !HasFlag(Flag.EMPLOYED)),
                check = SkillCheck(Skills.RAPPORT, 7, listOf(ConditionalBonus(HasFlag(Flag.BAD_REP), -2))),
                onMonth = listOf(grow(Skills.RAPPORT, 0.05)),
                onFailure = listOf(ChangeStat(Stats.SPIRIT, -2)),
                opens = listOf(
                    OpenSpec(Act.JOB_FACTORY, Rarity.UNCOMMON, 4, "job_hunt", text = "A foreman has a place for you."),
                    OpenSpec(Act.JOB_SHOP, Rarity.UNCOMMON, 4, "job_hunt", text = "A shop needs a clerk."),
                ),
                text = "You found leads.",
                failText = "Nobody called back.",
                leave = LeaveRule(keepPercent = 50, restartCooldownMonths = 1),
            ),
        )
        b.activity(job(Act.JOB_FACTORY, "Factory job", 90, listOf(Chance(0.04, ChangeStat(Stats.BODY, -1)))))
        b.activity(job(Act.JOB_SHOP, "Shop clerk", 70, listOf(grow(Skills.RAPPORT, 0.05))))
        b.activity(
            job(Act.JOB_ENGINEER, "Engineer's desk", 180, listOf(grow(Skills.INTELLECT, 0.05)), listOf(HasFlag(Flag.DEGREE))),
        )
        b.activity(job(Act.JOB_WORKSHOP, "Workshop hand", 110, listOf(grow(Skills.CRAFT, 0.08))))
    }

    private fun learning(b: LifeContentBuilder) {
        b.activity(
            activity(
                Act.STUDY_EVENING, "Evening classes", "study", 8,
                initiative = true,
                conditions = listOf(AgeBetween(12, 60)),
                monthlyCost = 10,
                check = SkillCheck(Skills.INTELLECT, 7),
                onMonth = listOf(grow(Skills.INTELLECT, 0.1)),
                onSuccess = listOf(grow(Skills.INTELLECT, 0.5)),
                opens = listOf(
                    OpenSpec(Act.PREP_EXAM, Rarity.UNCOMMON, 6, "study", text = "A teacher mentions the entrance exam."),
                ),
                leave = LeaveRule(keepPercent = 50, restartCooldownMonths = 2),
                intentViews = mapOf(Views.AMBITION to 0.3),
                text = "The course is behind you.",
                failText = "Too much slipped by.",
            ),
        )
        b.activity(
            activity(
                Act.PREP_EXAM, "Prepare for the exam", "study", 4,
                conditions = listOf(AgeBetween(14, 60), !HasFlag(Flag.DEGREE)),
                monthlyCost = 10,
                check = SkillCheck(Skills.INTELLECT, 6),
                onMonth = listOf(grow(Skills.INTELLECT, 0.1)),
                onSuccess = listOf(ScheduleIn(Evt.EXAM, 1, MissedPolicy.POSTPONE, 3)),
                onFailure = listOf(ChangeStat(Stats.SPIRIT, -2)),
                who = listOf(req(Role.MENTOR)),
                text = "You are ready.",
                failText = "You are not ready yet.",
            ),
        )
        b.activity(
            activity(
                Act.MENTOR_LESSONS, "Lessons with {npc.mentor}", "study", 6,
                who = listOf(req(Role.MENTOR, required = true)),
                onMonth = listOf(grow(Skills.INTELLECT, 0.15)),
                onSuccess = listOf(ChangeAttitudeOf(Role.MENTOR, 2)),
                opens = listOf(
                    OpenSpec(
                        Act.PREP_EXAM, Rarity.UNCOMMON, 6, "mentor", Role.MENTOR, LossRule.CONTINUE,
                        "{npc.mentor} says you could sit the exam.",
                    ),
                ),
                lost = LossRule.END,
                text = "The lessons end well.",
            ),
        )
        b.activity(
            activity(
                Act.APPRENTICESHIP, "Apprenticeship", "work", 10,
                who = listOf(req(Role.MENTOR, required = true)),
                onMonth = listOf(grow(Skills.CRAFT, 0.15), AddMoney(20)),
                onSuccess = listOf(ChangeAttitudeOf(Role.MENTOR, 2), ShiftView(Views.AMBITION, 1)),
                opens = listOf(OpenSpec(Act.JOB_WORKSHOP, Rarity.UNCOMMON, 5, "apprenticeship", text = "The workshop wants you.")),
                lost = LossRule.END,
                repeatable = false,
                tier = Tier.MEDIUM,
                text = "The apprenticeship is done.",
            ),
        )
        b.activity(
            activity(
                Act.READ_LIBRARY, "Read at the library", "study", 6,
                initiative = true,
                conditions = listOf(AgeBetween(8, 90)),
                onMonth = listOf(grow(Skills.INTELLECT, 0.08), Chance(0.1, ChangeStat(Stats.SPIRIT, 1))),
                onSuccess = listOf(grow(Skills.INTELLECT, 0.4)),
            ),
        )
        b.activity(
            activity(
                Act.START_PROJECT, "Start a project", "project", 12,
                initiative = true,
                conditions = listOf(AgeBetween(14, 70)),
                monthlyCost = 20,
                unpaid = UnpaidPolicy.DROP,
                check = SkillCheck(Skills.INTELLECT, 9, listOf(ConditionalBonus(SkillAtLeast(Skills.CRAFT, 4), 1))),
                onMonth = listOf(grow(Skills.CRAFT, 0.08)),
                onSuccess = listOf(AddMoney(500), ShiftView(Views.AMBITION, 1)),
                onFailure = listOf(ChangeStat(Stats.SPIRIT, -3), ShiftView(Views.AMBITION, -1)),
                who = listOf(req(Role.ALLY, Role.FRIEND, minAttitude = 0)),
                leave = LeaveRule(keepPercent = 30, restartCooldownMonths = 4, attitudeLoss = 2),
                tier = Tier.MEDIUM,
                text = "The project took off.",
                failText = "The project fizzled out.",
            ),
        )
        b.activity(
            activity(
                Act.VOLUNTEER, "Volunteer", "community", 6,
                initiative = true,
                conditions = listOf(AgeBetween(12, 80)),
                onMonth = listOf(grow(Skills.RAPPORT, 0.08), Chance(0.07, MeetNpc(Role.FRIEND, Role.ALLY, 1))),
                onSuccess = listOf(ShiftView(Views.KINSHIP, 1)),
            ),
        )
    }

    private fun body(b: LifeContentBuilder) {
        b.activity(
            activity(
                Act.TRAIN_BODY, "Train", "body", 6,
                initiative = true,
                conditions = listOf(AgeBetween(10, 80)),
                monthlyCost = 15,
                onMonth = listOf(Chance(0.25, ChangeStat(Stats.BODY, 1)), grow(Skills.GRIT, 0.08)),
                onSuccess = listOf(ChangeStat(Stats.BODY, 3)),
                leave = LeaveRule(keepPercent = 50, restartCooldownMonths = 3),
            ),
        )
        b.activity(
            activity(
                Act.REST_HOME, "Rest at home", "body", 2,
                initiative = true,
                conditions = listOf(AgeBetween(6, 100)),
                onMonth = listOf(ChangeStat(Stats.BODY, 2), ChangeStat(Stats.SPIRIT, 3)),
            ),
        )
        b.activity(
            activity(
                Act.ROAD_TRIP, "Road trip with {npc.ally}", "social", 2,
                conditions = listOf(AgeBetween(16, 70)),
                monthlyCost = 20,
                unpaid = UnpaidPolicy.DROP,
                who = listOf(req(Role.ALLY, Role.FRIEND, required = true)),
                onSuccess = listOf(
                    ShiftView(Views.KINSHIP, 1),
                    ChangeAttitudeOf(Role.ALLY, 1),
                    Chance(0.25, ScheduleIn(Evt.MEET_FIXER, 1)),
                ),
                lost = LossRule.END,
                unlock = Unlocks.ROAD,
            ),
        )
    }

    private fun band(b: LifeContentBuilder) {
        b.activity(
            activity(
                Act.GARAGE_BAND, "Garage band with {npc.ally}", "art", 10,
                conditions = listOf(AgeBetween(14, 45)),
                check = SkillCheck(
                    Skills.NERVE, 7, listOf(ConditionalBonus(NpcRemembers(Role.ALLY, MemoryTypes.SHARED_SUCCESS, 1), 1)),
                ),
                onMonth = listOf(grow(Skills.NERVE, 0.08)),
                onSuccess = listOf(ChangeAttitudeOf(Role.ALLY, 2), ShiftView(Views.KINSHIP, 1)),
                onFailure = listOf(ChangeAttitudeOf(Role.ALLY, -1)),
                who = listOf(req(Role.ALLY, Role.FRIEND, required = true)),
                opens = listOf(
                    OpenSpec(
                        Act.LOCAL_GIG, Rarity.UNCOMMON, 4, "band", Role.ALLY, LossRule.CONTINUE,
                        "The band is offered a stage.",
                    ),
                ),
                leave = LeaveRule(keepPercent = 30, restartCooldownMonths = 4, attitudeLoss = 3),
                lost = LossRule.END,
                tier = Tier.MEDIUM,
                text = "The band is tight now.",
                failText = "The band never gelled.",
            ),
        )
        b.activity(
            activity(
                Act.LOCAL_GIG, "Play a local gig", "art", 2,
                check = SkillCheck(Skills.NERVE, 8),
                onSuccess = listOf(AddMoney(120), ScheduleIn(Evt.SCOUT, 1)),
                onFailure = listOf(ChangeStat(Stats.SPIRIT, -2)),
                who = listOf(req(Role.ALLY, Role.FRIEND, required = true)),
                tier = Tier.MEDIUM,
                text = "The crowd loved it.",
                failText = "The gig fell flat.",
            ),
        )
        b.activity(
            activity(
                Act.STUDIO_CONTRACT, "Studio contract", "art", 12,
                conditions = listOf(AgeBetween(14, 50)),
                check = SkillCheck(Skills.NERVE, 9),
                onMonth = listOf(AddMoney(110), grow(Skills.NERVE, 0.06)),
                onSuccess = listOf(AddMoney(800), ShiftView(Views.AMBITION, 2)),
                onFailure = listOf(ChangeStat(Stats.SPIRIT, -4)),
                who = listOf(req(Role.ALLY, Role.FRIEND)),
                repeatable = false,
                tier = Tier.MAJOR,
                text = "The record is out.",
                failText = "The studio shelved the record.",
            ),
        )
    }

    private fun shady(b: LifeContentBuilder) {
        b.activity(
            activity(
                Act.SMALL_SCAM, "A small job for {npc.fixer}", "crime", 3,
                check = SkillCheck(Skills.NERVE, 8),
                onSuccess = listOf(AddMoney(300), RaiseTag(Tag.REBEL), ChangeAttitudeOf(Role.FIXER, 1)),
                onFailure = listOf(ScheduleIn(Evt.POLICE, 1), ChangeAttitudeOf(Role.FIXER, -1)),
                who = listOf(req(Role.FIXER, required = true)),
                opens = listOf(
                    OpenSpec(
                        Act.BIG_SCAM, Rarity.UNCOMMON, 4, "fixer", Role.FIXER, LossRule.END,
                        "{npc.fixer} has something bigger.",
                    ),
                ),
                leave = LeaveRule(restartCooldownMonths = 3, attitudeLoss = 2),
                lost = LossRule.END,
                text = "Clean and quick.",
                failText = "It went sideways.",
            ),
        )
        b.activity(
            activity(
                Act.BIG_SCAM, "The big job", "crime", 4,
                check = SkillCheck(Skills.NERVE, 10),
                onSuccess = listOf(AddMoney(1500), ScheduleIn(Evt.BETRAY, 2), RaiseTag(Tag.REBEL)),
                onFailure = listOf(ScheduleIn(Evt.POLICE, 1), AddMoney(-100)),
                who = listOf(req(Role.FIXER, required = true)),
                leave = LeaveRule(restartCooldownMonths = 3, attitudeLoss = 2),
                lost = LossRule.END,
                repeatable = false,
                tier = Tier.MAJOR,
                text = "A fortune changes hands.",
                failText = "The job collapses.",
            ),
        )
    }

    private fun events(b: LifeContentBuilder) {
        val passed = listOf<Effect>(
            MarkFlag(Flag.DEGREE),
            gain(Skills.INTELLECT),
            Remember(Role.MENTOR, "exam_passed", 2),
            OpenOpportunity(OpenSpec(Act.JOB_ENGINEER, Rarity.RARE, 4, "exam", text = "An engineering office wants {name}.")),
        )
        b.event(
            LifeEventDef(
                Evt.EXAM, Tier.MAJOR, "The day of the exam has come, {name}.",
                roles = listOf(req(Role.MENTOR)),
                weight = 0.0,
                choices = listOf(
                    ch(
                        "honest", "Sit the exam honestly",
                        check = SkillCheck(
                            Skills.INTELLECT, 8,
                            listOf(ConditionalBonus(NpcRemembers(Role.MENTOR, MemoryTypes.SHARED_SUCCESS, 1), 1)),
                        ),
                        ok = passed,
                        fail = listOf(
                            ChangeStat(Stats.SPIRIT, -3),
                            OpenOpportunity(OpenSpec(Act.PREP_EXAM, Rarity.COMMON, 8, "exam", text = "You can try again.")),
                        ),
                        text = "You passed.",
                        failText = "You failed.",
                    ),
                    ch(
                        "cheat", "Cheat",
                        check = SkillCheck(Skills.NERVE, 9),
                        ok = passed + RaiseTag(Tag.REBEL),
                        fail = listOf(
                            ChangeStat(Stats.SPIRIT, -5),
                            RaiseTag(Tag.REBEL),
                            ShiftView(Views.ORDER, -1),
                            ChangeAttitudeOf(Role.MENTOR, -3),
                        ),
                        text = "Nobody noticed.",
                        failText = "You were caught.",
                    ),
                    ch("skip", "Skip it", ok = listOf(ChangeStat(Stats.SPIRIT, -1)), text = "You stay home."),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.MEET_FIXER, Tier.MEDIUM, "A stranger at the market offers {name} easy money.",
                conditions = listOf(AgeBetween(16, 60), !HasNpc(Role.FIXER)),
                weight = 0.5,
                weightRules = listOf(WeightRule(HasTag(Tag.REBEL), 3.0)),
                cooldownMonths = 12,
                onTrigger = listOf(MeetNpc(Role.FIXER)),
                choices = listOf(
                    ch(
                        "listen", "Hear them out",
                        ok = listOf(
                            ChangeAttitudeOf(Role.FIXER, 1),
                            OpenOpportunity(
                                OpenSpec(
                                    Act.SMALL_SCAM, Rarity.UNCOMMON, 4, "fixer", Role.FIXER, LossRule.END,
                                    "{npc.fixer} lays out a small job.",
                                ),
                            ),
                        ),
                        text = "You listen.",
                    ),
                    ch("walk", "Walk away", ok = listOf(Remember(Role.FIXER, "snubbed", 1)), text = "You walk away."),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.POLICE, Tier.MAJOR, "The police come asking about {npc.fixer}.",
                weight = 0.0,
                choices = listOf(
                    ch(
                        "bribe", "Bribe them",
                        enabled = listOf(MoneyBetween(min = 200)),
                        locked = "Needs 200 money",
                        ok = listOf(AddMoney(-200)),
                        text = "They leave.",
                    ),
                    ch(
                        "run", "Run",
                        check = SkillCheck(Skills.GRIT, 8),
                        ok = listOf(ShiftView(Views.ORDER, -1)),
                        fail = listOf(ChangeStat(Stats.BODY, -10), AddMoney(-100)),
                        text = "You got away.",
                        failText = "They caught you.",
                    ),
                    ch(
                        "confess", "Confess",
                        ok = listOf(ChangeStat(Stats.SPIRIT, -4), ShiftView(Views.ORDER, 1), DropTag(Tag.REBEL)),
                        text = "You tell them everything.",
                    ),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.BETRAY, Tier.MAJOR, "{npc.fixer} has taken your share and vanished with a smile.",
                roles = listOf(req(Role.FIXER, required = true)),
                weight = 0.0,
                stillValid = listOf(NpcPresent(Role.FIXER)),
                choices = listOf(
                    ch("forgive", "Let it go", ok = listOf(AddMoney(-100)), text = "You let it go."),
                    ch(
                        "retaliate", "Get even",
                        check = SkillCheck(Skills.NERVE, 9),
                        ok = listOf(AddMoney(400), DepartNpc(Role.FIXER), Remember(Role.FIXER, "avenged", 2)),
                        fail = listOf(ChangeStat(Stats.BODY, -15), ChangeAttitudeOf(Role.FIXER, -5)),
                        text = "Justice, of a sort.",
                        failText = "It ended badly.",
                    ),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.SCOUT, Tier.MEDIUM, "A talent scout caught your set with {npc.ally}.",
                roles = listOf(req(Role.ALLY, Role.FRIEND)),
                weight = 0.0,
                choices = listOf(
                    ch(
                        "sign_alone", "Sign alone",
                        ok = listOf(
                            Remember(Role.ALLY, "left_behind", 2),
                            ChangeAttitudeOf(Role.ALLY, -2),
                            OpenOpportunity(OpenSpec(Act.STUDIO_CONTRACT, Rarity.RARE, 4, "scout", text = "A studio wants you.")),
                        ),
                        text = "You sign.",
                    ),
                    ch(
                        "sign_both", "Sign together",
                        visible = listOf(NpcPresent(Role.ALLY)),
                        ok = listOf(
                            ChangeAttitudeOf(Role.ALLY, 2),
                            OpenOpportunity(
                                OpenSpec(
                                    Act.STUDIO_CONTRACT, Rarity.RARE, 4, "scout", Role.ALLY, LossRule.CONTINUE,
                                    "A studio wants you both.",
                                ),
                            ),
                        ),
                        text = "You sign together.",
                    ),
                    ch("decline", "Decline", ok = listOf(ChangeStat(Stats.SPIRIT, -1)), text = "You decline."),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.BOSS_NOTICES, Tier.MEDIUM, "Your boss has noticed you.",
                conditions = listOf(HasFlag(Flag.EMPLOYED)),
                stillValid = listOf(HasFlag(Flag.EMPLOYED)),
                weight = 0.0,
                choices = listOf(
                    ch(
                        "ask", "Ask for a raise",
                        check = SkillCheck(Skills.RAPPORT, 8),
                        ok = listOf(AddMoney(200), ShiftView(Views.AMBITION, 1)),
                        fail = listOf(ChangeStat(Stats.SPIRIT, -3)),
                        text = "You get a raise.",
                        failText = "The boss laughs.",
                    ),
                    ch("quiet", "Keep your head down", ok = listOf(ShiftView(Views.ORDER, 1)), text = "You keep working."),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.LAYOFF, Tier.MEDIUM, "{name} is laid off.",
                conditions = listOf(HasFlag(Flag.EMPLOYED), AgeBetween(18, 64)),
                weight = 0.5,
                weightRules = listOf(WeightRule(HasTag(Tag.REBEL), 2.0)),
                cooldownMonths = 18,
                group = "work",
                onTrigger = listOf(com.znova.engine.rules.ClearFlag(Flag.EMPLOYED)),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.ILLNESS, Tier.MEDIUM, "{name} is struck down by illness.",
                weight = 0.4,
                weightRules = listOf(WeightRule(AgeBetween(50), 2.0)),
                choices = listOf(
                    ch(
                        "treat", "Get treatment",
                        enabled = listOf(MoneyBetween(min = 80)),
                        locked = "Needs 80 money",
                        ok = listOf(AddMoney(-80), ChangeStat(Stats.BODY, -2)),
                        text = "You pulled through.",
                    ),
                    ch(
                        "endure", "Tough it out",
                        check = SkillCheck(Skills.GRIT, 8),
                        ok = listOf(ChangeStat(Stats.BODY, -5)),
                        fail = listOf(ChangeStat(Stats.BODY, -20)),
                        text = "You made it through.",
                        failText = "Your body gave out.",
                    ),
                    ch(
                        "bed_rest", "Stay in bed",
                        ok = listOf(ChangeStat(Stats.BODY, -8), ChangeStat(Stats.SPIRIT, -2)),
                        text = "Weeks in bed.",
                    ),
                ),
            ),
        )
        friendEvents(b)
        smallEvents(b)
    }

    private fun friendEvents(b: LifeContentBuilder) {
        b.event(
            LifeEventDef(
                Evt.QUARREL, Tier.MEDIUM, "{name} and {npc.ally} had an argument.",
                roles = listOf(req(Role.ALLY, Role.FRIEND, required = true, minAttitude = -3)),
                conditions = listOf(AgeBetween(12, 80)),
                stillValid = listOf(NpcPresent(Role.ALLY)),
                weight = 0.4,
                cooldownMonths = 8,
                choices = listOf(
                    ch(
                        "apologize", "Apologize",
                        ok = listOf(ChangeAttitudeOf(Role.ALLY, 2), Remember(Role.ALLY, "apologized", 1)),
                        text = "You made peace.",
                    ),
                    ch(
                        "stand", "Stand your ground",
                        check = SkillCheck(Skills.RAPPORT, 7),
                        ok = listOf(ChangeAttitudeOf(Role.ALLY, -1), ShiftView(Views.AMBITION, 1)),
                        fail = listOf(ChangeAttitudeOf(Role.ALLY, -3), Remember(Role.ALLY, "quarrel", 2)),
                        text = "They back down.",
                        failText = "It gets ugly.",
                    ),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.IN_NEED, Tier.MEDIUM, "{npc.ally} asks {name} for help.",
                roles = listOf(req(Role.ALLY, Role.FRIEND, required = true, minAttitude = 0)),
                stillValid = listOf(NpcPresent(Role.ALLY)),
                weight = 0.3,
                cooldownMonths = 10,
                choices = listOf(
                    ch(
                        "help", "Help",
                        enabled = listOf(MoneyBetween(min = 100)),
                        locked = "Needs 100 money",
                        ok = listOf(AddMoney(-100), ChangeAttitudeOf(Role.ALLY, 3), Remember(Role.ALLY, "helped", 2)),
                        text = "You help out.",
                    ),
                    ch(
                        "refuse", "Refuse",
                        ok = listOf(ChangeAttitudeOf(Role.ALLY, -2), Remember(Role.ALLY, "refused", 1)),
                        text = "You say no.",
                    ),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.GOSSIP, Tier.MINOR, "Word gets around that {name} drops people halfway.",
                conditions = listOf(NpcRemembers(null, MemoryTypes.ABANDONED, 1, 18), !HasFlag(Flag.BAD_REP)),
                weight = 0.4,
                maxPerLife = 1,
                onTrigger = listOf(MarkFlag(Flag.BAD_REP)),
            ),
        )
    }

    private fun smallEvents(b: LifeContentBuilder) {
        b.event(
            LifeEventDef(
                Evt.WINDFALL, Tier.MINOR, "{name} finds some money in an old coat.",
                conditions = listOf(AgeBetween(8, 100)),
                cooldownMonths = 8,
                onTrigger = listOf(AddMoney(40)),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.STREET_FIGHT, Tier.MEDIUM, "Trouble finds {name} on the street.",
                conditions = listOf(AgeBetween(14, 40)),
                weight = 0.3,
                choices = listOf(
                    ch(
                        "fight", "Fight",
                        check = SkillCheck(Skills.GRIT, 7),
                        ok = listOf(ShiftView(Views.AMBITION, 1), ChangeStat(Stats.BODY, -2)),
                        fail = listOf(ChangeStat(Stats.BODY, -10)),
                        text = "You hold your own.",
                        failText = "You get beaten.",
                    ),
                    ch(
                        "talk", "Talk it down",
                        check = SkillCheck(Skills.RAPPORT, 8),
                        fail = listOf(ChangeStat(Stats.SPIRIT, -2)),
                        text = "They back off.",
                        failText = "Words do not help.",
                    ),
                    ch("flee", "Run", ok = listOf(ChangeStat(Stats.SPIRIT, -1)), text = "You run."),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.RAINY, Tier.MINOR, "It rains for weeks.",
                onTrigger = listOf(ChangeStat(Stats.SPIRIT, -1)),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.INSPIRATION, Tier.MINOR, "A lecture clicks into place.",
                conditions = listOf(HasPursuit(Act.STUDY_EVENING)),
                cooldownMonths = 6,
                onTrigger = listOf(gain(Skills.INTELLECT)),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.HEADHUNTER, Tier.MEDIUM, "A headhunter calls {name}.",
                conditions = listOf(HasFlag(Flag.DEGREE), !HasFlag(Flag.EMPLOYED)),
                weight = 0.3,
                cooldownMonths = 12,
                choices = listOf(
                    ch(
                        "interview", "Take the interview",
                        ok = listOf(
                            OpenOpportunity(OpenSpec(Act.JOB_ENGINEER, Rarity.RARE, 3, "headhunter", text = "An office wants {name}.")),
                        ),
                        text = "You go to the interview.",
                    ),
                    ch("decline", "Decline", text = "You hang up."),
                ),
            ),
        )
        b.event(
            LifeEventDef(
                Evt.MEMORY_LANE, Tier.MINOR, "{name} flips through a scrapbook of a life that was not theirs.",
                weight = 0.2,
                onTrigger = listOf(ChangeStat(Stats.SPIRIT, 1)),
                requiresUnlock = Unlocks.SCRAPBOOK,
            ),
        )
    }

    private fun people(b: LifeContentBuilder) {
        val friend = req(Role.ALLY, Role.FRIEND)
        b.reaction(
            NpcReactionDef(
                "friend_offers_band", req(Role.ALLY, Role.FRIEND, minAttitude = 1),
                NpcAct.Offer(
                    OpenSpec(
                        Act.GARAGE_BAND, Rarity.UNCOMMON, 5, "friend", Role.ALLY, LossRule.END,
                        "{npc.ally} wants to start a band.",
                    ),
                ),
                conditions = listOf(AgeBetween(14, 45), !HasPursuit(Act.GARAGE_BAND), !OpportunityOpen(Act.GARAGE_BAND)),
                chance = 0.12,
                cooldownMonths = 18,
            ),
        )
        b.reaction(
            NpcReactionDef(
                "friend_asks_help", req(Role.ALLY, Role.FRIEND, minAttitude = 0), NpcAct.Raise(Evt.IN_NEED),
                chance = 0.08,
                cooldownMonths = 10,
            ),
        )
        b.reaction(
            NpcReactionDef(
                "friend_takes_offence", friend,
                NpcAct.Apply(listOf(ChangeAttitudeOf(Role.ALLY, -2), Remember(Role.ALLY, "grudge", 2))),
                conditions = listOf(NpcRemembers(Role.ALLY, MemoryTypes.ABANDONED, 1, 6)),
                chance = 0.35,
                cooldownMonths = 8,
                text = "{npc.ally} is sore about it.",
            ),
        )
        b.reaction(
            NpcReactionDef(
                "friend_moves_away", friend, NpcAct.Depart,
                conditions = listOf(NpcAttitudeBetween(Role.ALLY, max = -4)),
                chance = 0.3,
                cooldownMonths = 24,
                text = "{npc.ally} moved away.",
            ),
        )
        b.reaction(
            NpcReactionDef(
                "mentor_offers_lessons", req(Role.MENTOR),
                NpcAct.Offer(
                    OpenSpec(
                        Act.MENTOR_LESSONS, Rarity.UNCOMMON, 4, "mentor", Role.MENTOR, LossRule.END,
                        "{npc.mentor} offers to teach you.",
                    ),
                ),
                conditions = listOf(!HasPursuit(Act.MENTOR_LESSONS), !OpportunityOpen(Act.MENTOR_LESSONS)),
                chance = 0.12,
                cooldownMonths = 12,
            ),
        )
        b.reaction(
            NpcReactionDef(
                "mentor_offers_apprenticeship", req(Role.MENTOR),
                NpcAct.Offer(
                    OpenSpec(
                        Act.APPRENTICESHIP, Rarity.UNCOMMON, 4, "mentor", Role.MENTOR, LossRule.END,
                        "{npc.mentor} could take you on.",
                    ),
                ),
                conditions = listOf(SkillAtLeast(Skills.CRAFT, 3), !HasPursuit(Act.APPRENTICESHIP)),
                chance = 0.1,
                cooldownMonths = 12,
            ),
        )
        b.reaction(
            NpcReactionDef(
                "fixer_calls", req(Role.FIXER, minAttitude = 0),
                NpcAct.Offer(
                    OpenSpec(
                        Act.SMALL_SCAM, Rarity.UNCOMMON, 3, "fixer", Role.FIXER, LossRule.END,
                        "{npc.fixer} has another job.",
                    ),
                ),
                conditions = listOf(!HasPursuit(Act.SMALL_SCAM), !HasPursuit(Act.BIG_SCAM), !OpportunityOpen(Act.SMALL_SCAM)),
                chance = 0.1,
                cooldownMonths = 8,
            ),
        )
        b.interaction(
            InteractionDef(
                InteractKind.TALK, Role.MENTOR,
                effects = listOf(ChangeAttitudeOf(TARGET, 1), grow(Skills.INTELLECT, 0.3)),
                remember = "talked",
                resultText = "{npc.target} shares some advice.",
            ),
        )
        b.interaction(
            InteractionDef(
                InteractKind.TALK, Role.FIXER,
                effects = listOf(
                    Chance(
                        0.4,
                        OpenOpportunity(
                            OpenSpec(
                                Act.SMALL_SCAM, Rarity.UNCOMMON, 3, "fixer", Role.FIXER, LossRule.END,
                                "{npc.fixer} has a job for you.",
                            ),
                        ),
                    ),
                ),
                remember = "talked",
                resultText = "{npc.target} grins.",
            ),
        )
        b.interaction(
            InteractionDef(
                InteractKind.TALK, Role.FRIEND,
                effects = listOf(ChangeAttitudeOf(TARGET, 1)),
                remember = "talked",
                resultText = "You talk with {npc.target}.",
            ),
        )
        b.interaction(
            InteractionDef(
                InteractKind.HELP, Role.FRIEND,
                conditions = listOf(MoneyBetween(min = 30)),
                effects = listOf(AddMoney(-30), ChangeAttitudeOf(TARGET, 2)),
                remember = "helped",
                memoryWeight = 2,
                resultText = "You help {npc.target}.",
            ),
        )
        b.interaction(
            InteractionDef(
                InteractKind.ASK, Role.FRIEND,
                conditions = listOf(NpcAttitudeBetween(TARGET, min = 3)),
                effects = listOf(AddMoney(40), ChangeAttitudeOf(TARGET, -1)),
                remember = "asked",
                resultText = "{npc.target} lends you a little.",
            ),
        )
    }

    private fun world(b: LifeContentBuilder) {
        b.opportunity(
            OpportunityGen(
                "gen_factory",
                OpenSpec(Act.JOB_FACTORY, Rarity.COMMON, 4, "gen", text = "A factory is hiring."),
                conditions = listOf(AgeBetween(16, 60), !HasFlag(Flag.EMPLOYED)),
                cooldownMonths = 4,
            ),
        )
        b.opportunity(
            OpportunityGen(
                "gen_shop",
                OpenSpec(Act.JOB_SHOP, Rarity.COMMON, 4, "gen", text = "A shop has a vacancy."),
                conditions = listOf(AgeBetween(16, 60), !HasFlag(Flag.EMPLOYED)),
                cooldownMonths = 4,
            ),
        )
        b.opportunity(
            OpportunityGen(
                "gen_road_trip",
                OpenSpec(Act.ROAD_TRIP, Rarity.UNCOMMON, 3, "gen", Role.ALLY, LossRule.END, "{npc.ally} wants to get out of town."),
                conditions = listOf(AgeBetween(16, 70)),
                weight = 0.6,
                cooldownMonths = 6,
                participant = req(Role.ALLY, Role.FRIEND, minAttitude = 1),
            ),
        )
        b.upkeep(When(AgeBetween(16), AddMoney(-40)))
        b.upkeep(When(AgeBetween(50), Chance(0.1, ChangeStat(Stats.BODY, -1))))
        b.upkeep(When(MoneyBetween(max = -1), ChangeStat(Stats.SPIRIT, -1)))
        b.scenario(
            LifeScenario(
                id = "city_youth",
                startAgeYears = 14..25,
                skills = mapOf(Skills.INTELLECT to 2, Skills.RAPPORT to 2, Skills.GRIT to 1),
                views = mapOf(Views.KINSHIP to 1),
                money = 60,
                people = listOf(
                    ScenarioPerson(Role.FRIEND, 3, name = "Anya", memories = listOf(ScenarioMemory(MemoryTypes.SHARED_SUCCESS, 6))),
                    ScenarioPerson(Role.FRIEND, 1, name = "Boris"),
                    ScenarioPerson(Role.MENTOR, 2, name = "Mr. Volkov"),
                ),
                past = listOf(ScenarioPast(11, Tier.MEDIUM, "Moved to a new town.")),
            ),
        )
    }

    private val TARGET = RoleKey("target")
}
