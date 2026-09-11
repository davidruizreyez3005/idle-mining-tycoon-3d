package com.idlemining.tycoon3d.game.scene

import io.github.sceneview.node.Node

/**
 * Mutable node references captured from the declarative scene at build time.
 * [SceneAnimator] writes transforms onto these nodes every frame — the Compose
 * tree itself stays static (no recomposition while the game renders).
 */
class SceneRefs(val nodeCount: Int) {

    class WorkerNodes {
        var root: Node? = null
        var body: Node? = null
        var head: Node? = null
        var backpack: Node? = null
        val legPivots: Array<Node?> = arrayOfNulls(2)
        val armPivots: Array<Node?> = arrayOfNulls(2)
        var pickaxe: Node? = null
    }

    class NodeVisuals {
        var root: Node? = null
        var body: Node? = null
        var crystalGroup: Node? = null
    }

    val worker = WorkerNodes()
    val nodes: Array<NodeVisuals> = Array(nodeCount) { NodeVisuals() }

    /** Decorative drop spheres flying to the worker (pool). */
    val drops: Array<Node?> = arrayOfNulls(DROP_POOL)

    /** Rock fragment cubes with fake physics (pool). */
    val fragments: Array<Node?> = arrayOfNulls(FRAGMENT_POOL)

    /** Depot sign — bobs and spins. */
    var depotSign: Node? = null

    /** Auto-Extractor machine near the mine entrance. */
    var extractor: Node? = null
    var extractorDrill: Node? = null

    companion object {
        const val DROP_POOL = 10
        const val FRAGMENT_POOL = 18
    }
}
