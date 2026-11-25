package com.znova.engine.world

import com.znova.engine.core.SkillKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.ViewKey

data class StatDef(val key: StatKey, val name: String, val range: IntRange = 0..100, val start: Int = 60)
data class SkillDef(val key: SkillKey, val name: String, val range: IntRange = 0..10)
data class ViewDef(val key: ViewKey, val name: String, val range: IntRange = -10..10)

class WorldSchema(
    stats: List<StatDef>,
    skills: List<SkillDef>,
    views: List<ViewDef>,
    val vitalStat: StatKey,
    val vitalDeathCause: String = "vital_failure",
    val startSkillPoints: Int = 6,
    val startSkillCap: Int = 4,
    val viewSpread: Int = 2,
    val maxTags: Int = 5,
) {
    // Preserve declaration order for deterministic iteration.
    val stats = stats.associateBy { it.key }
    val skills = skills.associateBy { it.key }
    val views = views.associateBy { it.key }

    init {
        require(vitalStat in this.stats) { "vitalStat ${vitalStat.id} is not declared" }
    }

    fun clampStat(k: StatKey, v: Int) = v.coerceIn(stats.getValue(k).range)
    fun clampSkill(k: SkillKey, v: Int) = v.coerceIn(skills.getValue(k).range)
    fun clampView(k: ViewKey, v: Int) = v.coerceIn(views.getValue(k).range)
}
