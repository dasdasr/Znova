package com.znova.engine.meta

import com.znova.engine.content.AchievementDef
import com.znova.engine.content.FocusDef
import com.znova.engine.content.LegacyDef
import com.znova.engine.content.OriginDef
import com.znova.engine.content.PlaceDef
import com.znova.engine.content.UnlockDef
import com.znova.engine.core.AchievementId
import com.znova.engine.core.CounterKey
import com.znova.engine.core.FlagKey
import com.znova.engine.core.FocusId
import com.znova.engine.core.LegacyId
import com.znova.engine.core.OriginId
import com.znova.engine.core.PlaceId
import com.znova.engine.core.TagKey
import com.znova.engine.core.UnlockId
import com.znova.engine.state.ChronicleEntry
import com.znova.engine.state.WorldDate

data class LifeSummary(
    val name: String,
    val packId: String,
    val placeId: PlaceId,
    val title: String,
    val ageYears: Int,
    val cause: String,
    val highlights: List<ChronicleEntry>,
    val tags: Set<TagKey>,
    val seed: Long,
    val flags: Set<FlagKey> = emptySet(),
    val counters: Map<CounterKey, Int> = emptyMap(),
)

data class MetaProfile(
    val unlocks: Set<UnlockId> = emptySet(),
    val achievements: Set<AchievementId> = emptySet(),
    val archive: List<LifeSummary> = emptyList(),
)

data class LifeSetup(
    val seed: Long,
    val name: String? = null,
    val place: PlaceId? = null,
    val origin: OriginId? = null,
    val startDate: WorldDate? = null,
    val focus: FocusId? = null,
    val legacies: List<LegacyId> = emptyList(),
)

data class ConcludeResult(
    val meta: MetaProfile,
    val summary: LifeSummary,
    val newAchievements: List<AchievementDef>,
    val newUnlocks: List<UnlockDef>,
    val archiveGaps: List<String>,
)

data class LaboratoryView(
    val places: List<PlaceDef>,
    val origins: List<OriginDef>,
    val legacies: List<LegacyDef>,
    val foci: List<FocusDef>,
    val legacySlots: Int,
    val openGoals: List<AchievementDef>,
    val archive: List<LifeSummary>,
)
