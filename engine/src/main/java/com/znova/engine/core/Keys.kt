package com.znova.engine.core

@JvmInline
value class StatKey(val id: String)
@JvmInline
value class SkillKey(val id: String)
@JvmInline
value class ViewKey(val id: String)
@JvmInline
value class TagKey(val id: String)
@JvmInline
value class FlagKey(val id: String)
@JvmInline
value class RoleKey(val id: String)
@JvmInline
value class LawKey(val id: String)
@JvmInline
value class CountryId(val id: String)
@JvmInline
value class OriginId(val id: String)
@JvmInline
value class EventId(val id: String)
@JvmInline
value class ChoiceId(val id: String)
@JvmInline
value class ActivityId(val id: String)
@JvmInline
value class LegacyId(val id: String)
@JvmInline
value class AchievementId(val id: String)
@JvmInline
value class UnlockId(val id: String)
@JvmInline
value class NpcId(val id: String)

object Stats {
    val BODY = StatKey("body")
    val SPIRIT = StatKey("spirit")
    val ALL = listOf(BODY, SPIRIT)
}

object Skills {
    val AWARENESS = SkillKey("awareness")
    val INTELLECT = SkillKey("intellect")
    val EXPRESSION = SkillKey("expression")
    val RAPPORT = SkillKey("rapport")
    val INFLUENCE = SkillKey("influence")
    val CRAFT = SkillKey("craft")
    val PHYSICALITY = SkillKey("physicality")
    val ADAPTABILITY = SkillKey("adaptability")
    val ALL = listOf(
        AWARENESS,
        INTELLECT,
        EXPRESSION,
        RAPPORT,
        INFLUENCE,
        CRAFT,
        PHYSICALITY,
        ADAPTABILITY
    )
}

object Views {
    val KINSHIP = ViewKey("kinship")
    val ORDER = ViewKey("order")
    val AMBITION = ViewKey("ambition")
    val FAITH = ViewKey("faith")
    val HARDNESS = ViewKey("hardness")
    val ALL = listOf(KINSHIP, ORDER, AMBITION, FAITH, HARDNESS)
}

object Balance {
    val STAT = 0..100
    val SKILL = 0..10
    val VIEW = -10..10
    const val MAX_TAGS = 5
}

@JvmInline
value class CounterKey(val id: String)
@JvmInline
value class TrackerId(val id: String)
@JvmInline
value class FocusId(val id: String)
