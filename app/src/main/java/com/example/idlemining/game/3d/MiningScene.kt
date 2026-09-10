package com.example.idlemining.game.threed

import android.content.Context
import io.sceneview.SceneView
import io.sceneview.material.Material
import io.sceneview.math.Vector3
import io.sceneview.node.CubeNode
import io.sceneview.node.ModelNode
import io.sceneview.node.Node

class MiningScene(private val context: Context) {
    val sceneView = SceneView(context)

    init {
        sceneView.apply {
            scene = io.sceneview.Scene(this)
        }
        setupLighting()
        createShaft()
        createConveyor()
        createCart()
    }

    private fun setupLighting() {
        val scene = sceneView.scene
        scene.environment?.indirectLight = io.sceneview.light.IndirectLight()
        scene.addChildNode(
            io.sceneview.node.DirectionalLight(context).apply {
                intensity = 20000f
                direction = Vector3(-0.5f, -1f, -0.5f)
                shadowEnabled = true
            }
        )
    }

    private fun createShaft() {
        val shaft = CubeNode(sceneView.engine, 2.0f, 1.0f, 4.0f).apply {
            material = Material(context).apply { parameters = mapOf("baseColor" to android.graphics.Color.parseColor("#8B4513")) }
            position = Vector3(0f, 0f, 0f)
        }
        sceneView.scene.addChildNode(shaft)
    }

    private fun createConveyor() {
        val conveyor = CubeNode(sceneView.engine, 6.0f, 0.2f, 0.2f).apply {
            material = Material(context).apply { parameters = mapOf("baseColor" to android.graphics.Color.parseColor("#555555")) }
            position = Vector3(3f, 0.1f, 0f)
        }
        sceneView.scene.addChildNode(conveyor)
    }

    private fun createCart() {
        val cart = CubeNode(sceneView.engine, 0.8f, 0.8f, 0.8f).apply {
            material = Material(context).apply { parameters = mapOf("baseColor" to android.graphics.Color.parseColor("#FFD700")) }
            position = Vector3(-1f, 1f, 0f)
        }
        sceneView.scene.addChildNode(cart)
    }
}
