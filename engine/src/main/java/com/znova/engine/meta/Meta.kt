package com.znova.engine.meta

import com.znova.engine.content.*
import com.znova.engine.core.*
import com.znova.engine.state.ChronicleEntry

data class LifeSummary(
    val name: String,
    val countryId: CountryId,
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
    val country: CountryId? = null,
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
    val countries: List<CountryDef>,
    val origins: List<OriginDef>,
    val legacies: List<LegacyDef>,
    val foci: List<FocusDef>,
    val legacySlots: Int,
    val openGoals: List<AchievementDef>,
    val archive: List<LifeSummary>,
)
