package com.idleshaft.tycoon.domain

/**
 * Unidirectional Data Flow: the UI sends [GameIntent]s into the engine; the engine
 * reduces them against [GameState] and emits new state (+ one-shot [GameEvent]s for
 * transient presentation like cash popups).
 */
sealed interface GameIntent {
    /** Queue one manual work cycle on every unlocked, un-automated shaft. */
    data object TapMine : GameIntent

    /** Run the refinery while raw ore is available (until starved). */
    data object TapRefine : GameIntent

    /** Run delivery trucks while bars are available (until starved). */
    data object TapDeliver : GameIntent

    /** Unlock the next shaft (paid with cash). */
    data object UnlockShaft : GameIntent

    /** Buy the next level of a shaft-scoped upgrade. */
    data class BuyShaftUpgrade(val shaftIndex: Int, val kind: Economy.UpgradeKind) : GameIntent

    /** Buy the next level of a global upgrade. */
    data class BuyGlobalUpgrade(val kind: Economy.UpgradeKind) : GameIntent

    /** Hire the manager for one shaft (automates that shaft). */
    data class HireShaftManager(val shaftIndex: Int) : GameIntent

    /** Hire the refinery manager. */
    data object HireRefineryManager : GameIntent

    /** Hire the logistics manager (trucks). */
    data object HireLogisticsManager : GameIntent

    /** Spend gems on a temporary 2x income boost. */
    data object ActivateBoost : GameIntent

    /** Clear the pending offline-earnings presentation. */
    data object DismissOfflineEarnings : GameIntent
}

/**
 * One-shot side-channel events emitted by the engine (not part of the persisted state).
 */
sealed interface GameEvent {
    /** A truck sold its cargo — spawn a floating `+$` popup. */
    data class CashPopup(val amount: Double) : GameEvent

    /** The player crossed a total-earnings milestone and earned a gem. */
    data class GemMilestone(val totalGems: Int) : GameEvent

    /** The offline earnings were computed and applied at load time. */
    data class OfflineEarnings(val amount: Double, val seconds: Long) : GameEvent

    /** Not enough cash to complete a purchase. */
    data object PurchaseFailed : GameEvent
}
