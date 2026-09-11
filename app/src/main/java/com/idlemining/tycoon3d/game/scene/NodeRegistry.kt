package com.idlemining.tycoon3d.game.scene

import io.github.sceneview.node.Node

/**
 * Maps scene nodes to tappable world entities. The SceneView gesture dispatcher
 * hit-tests against node colliders and hands us the deepest hit node; this
 * registry walks up the parent chain to find the registered root.
 */
class NodeRegistry {

    enum class Kind { GROUND, NODE, DEPOT, WORKER, PROP, NONE }

    private val kinds = HashMap<Node, Kind>()
    private val nodeIndex = HashMap<Node, Int>()

    fun registerGround(root: Node) { kinds[root] = Kind.GROUND }

    fun registerNode(root: Node, index: Int) {
        kinds[root] = Kind.NODE
        nodeIndex[root] = index
    }

    fun registerDepot(root: Node) { kinds[root] = Kind.DEPOT }

    fun registerWorker(root: Node) { kinds[root] = Kind.WORKER }

    fun registerProp(root: Node) { kinds[root] = Kind.PROP }

    data class Hit(val kind: Kind, val nodeIndex: Int = -1)

    fun resolve(node: Node?): Hit {
        var current: Node? = node
        var depth = 0
        while (current != null && depth < 16) {
            val kind = kinds[current] ?: Kind.NONE
            if (kind != Kind.NONE) {
                return Hit(kind, nodeIndex[current] ?: -1)
            }
            current = current.parent
            depth++
        }
        return Hit(Kind.NONE)
    }
}
