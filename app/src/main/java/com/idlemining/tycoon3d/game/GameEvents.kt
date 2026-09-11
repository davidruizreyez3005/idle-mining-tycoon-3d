package com.idlemining.tycoon3d.game

/**
 * Unidirectional Data Flow: the UI (and the 3D tap handler) only ever dispatch
 * [GameIntent]s; results flow back through [GameState] (StateFlow) and one-shot
 * [GameEvent]s (SharedFlow).
 */
sealed class GameIntent {
    /** Tap on empty ground — walk there. */
    data class TapGround(val x: Float, val z: Float) : GameIntent()

    /** Tap on a mine node — walk to it and start mining. */
    data class TapNode(val index: Int) : GameIntent()

    /** Tap on the depot (or the SELL button) — walk there and sell everything. */
    object TapDepot : GameIntent()

    data class BuyUpgrade(val id: String) : GameIntent()

    object DismissOffline : GameIntent()

    object MarkHintShown : GameIntent()
}

/** One-shot presentation events, emitted by the simulation. */
sealed class GameEvent {
    /** Small floating text near the top-center of the HUD. */
    data class Popup(val text: String, val kind: PopupKind = PopupKind.INFO) : GameEvent()

    data class Sold(val amount: Double, val units: Int) : GameEvent()

    data class NodeBroken(val index: Int, val yields: Map<String, Int>) : GameEvent()

    data class UpgradeBought(val id: String, val level: Int) : GameEvent()

    object BackpackFull : GameEvent()
}

enum class PopupKind { INFO, MONEY, WARN }
