package com.znova.engine.life

import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.NpcId
import com.znova.engine.core.RoleKey
import com.znova.engine.state.LifeState

sealed interface PlayerCommand {
    data class Start(
        val activity: ActivityId,
        val opportunity: String? = null,
        val participants: Map<RoleKey, NpcId> = emptyMap(),
    ) : PlayerCommand

    data class Replace(
        val pursuit: String,
        val activity: ActivityId,
        val opportunity: String? = null,
        val participants: Map<RoleKey, NpcId> = emptyMap(),
    ) : PlayerCommand

    data class Drop(val pursuit: String) : PlayerCommand

    data class Invite(
        val pursuit: String,
        val slot: RoleKey,
        val npc: NpcId,
    ) : PlayerCommand

    data class Interact(
        val npc: NpcId,
        val kind: InteractKind,
    ) : PlayerCommand

    data class Resolve(
        val decision: String,
        val choice: ChoiceId,
    ) : PlayerCommand

    data object DismissIntent : PlayerCommand
}

enum class Reject {
    LIFE_ENDED,
    NOT_MONTHLY,
    DECISION_PENDING,
    NOT_CURRENT,
    UNKNOWN_PURSUIT,
    UNKNOWN_ACTIVITY,
    UNKNOWN_OPPORTUNITY,
    UNKNOWN_NPC,
    UNKNOWN_DECISION,
    UNKNOWN_CHOICE,
    CHOICE_UNAVAILABLE,
    NO_FREE_SLOT,
    NOT_AVAILABLE,
    CONDITIONS_NOT_MET,
    NOT_ENOUGH_MONEY,
    ALREADY_RUNNING,
    NOT_REPEATABLE,
    ON_COOLDOWN,
    SLOT_NOT_FOUND,
    SLOT_TAKEN,
    PARTICIPANT_MISSING,
    PARTICIPANT_UNFIT,
    PARTICIPANT_REFUSES,
    NPC_NOT_PRESENT,
    NOTHING_NEW,
    NO_INTENT,
}

sealed interface CommandResult {
    data class Ok(val state: LifeState) : CommandResult

    data class Rejected(
        val state: LifeState,
        val reason: Reject,
        val detail: String? = null,
    ) : CommandResult
}

data class TickResult(
    val state: LifeState,
    val advanced: Boolean,
    val stop: Stop?,
)
