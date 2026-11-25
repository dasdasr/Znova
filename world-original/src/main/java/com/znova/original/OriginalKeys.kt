package com.znova.original

import com.znova.engine.core.SkillKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.ViewKey
import com.znova.engine.world.SkillDef
import com.znova.engine.world.StatDef
import com.znova.engine.world.ViewDef
import com.znova.engine.world.WorldSchema
import kotlin.collections.map

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
    val ALL =
        listOf(
            AWARENESS,
            INTELLECT,
            EXPRESSION,
            RAPPORT,
            INFLUENCE,
            CRAFT,
            PHYSICALITY,
            ADAPTABILITY,
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

val OriginalSchema =
    WorldSchema(
        stats =
            Stats.ALL.map {
                StatDef(it, it.id.replaceFirstChar(Char::uppercase), 0..100, start = 60)
            },
        skills =
            Skills.ALL.map {
                SkillDef(it, it.id)
            },
        views =
            Views.ALL.map {
                ViewDef(it, it.id)
            },
        vitalStat = Stats.BODY,
        vitalDeathCause = "body_failure",
        startSkillPoints = 6,
        startSkillCap = 4,
        maxTags = 5,
    )
