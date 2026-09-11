package com.idleshaft.tycoon.game.threed

import androidx.compose.runtime.Composable
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.setIndices
import io.github.sceneview.geometries.setVertices
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.MeshNode
import kotlin.math.sqrt

/**
 * Custom low-poly toon meshes (unit-sized, node-scaled) that go beyond the
 * stock cube/sphere/cylinder primitives:
 *
 *  - [wedge]      right-angle ramp   — roofs, chutes, slopes
 *  - [pyramid]    square pyramid     — hopper, roofs, accents
 *  - [ingot]      trapezoid prism    — refined metal bars
 *  - [rock]       faceted boulder    — ore chunks, heaps, decor
 *
 * Each [ToonMesh] owns one Filament [VertexBuffer]/[IndexBuffer] pair that can
 * back any number of [MeshNode] renderables — the buffers are built once and
 * shared, so hundreds of rocks cost almost nothing.
 */
class ToonMesh internal constructor(
    internal val vertexBuffer: VertexBuffer,
    internal val indexBuffer: IndexBuffer,
    internal val boundingBox: Box,
)

class ToonShapes(private val engine: Engine) {

    private val cache = HashMap<String, ToonMesh>()

    /** Unit ramp: footprint 1×1, full height at +Z, sloping to ground at −Z. */
    val wedge: ToonMesh by lazy { build("wedge", wedgeVertices(), quadStripIndices(3, 2)) }

    /** Unit pyramid: 1×1 base at y=−0.5, apex at (0, +0.5, 0). */
    val pyramid: ToonMesh by lazy { build("pyramid", pyramidVertices(), quadStripIndices(1, 4)) }

    /** Unit ingot: trapezoid prism, long axis X, 0.32 tall. */
    val ingot: ToonMesh by lazy { build("ingot", ingotVertices(), quadStripIndices(6)) }

    /** Irregular faceted boulder (4 deterministic variants). */
    fun rock(variant: Int): ToonMesh {
        val v = ((variant % 4) + 4) % 4
        return cache.getOrPut("rock$v") { build("rock$v", rockVertices(v), rockIndices()) }
    }

    // ------------------------------------------------------------------ build

    private fun build(key: String, vertices: List<Geometry.Vertex>, indices: List<Int>): ToonMesh {
        val vb = VertexBuffer.Builder()
            .bufferCount(2)
            .vertexCount(vertices.size)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, POSITION_STRIDE)
            .attribute(VertexAttribute.TANGENTS, 1, AttributeType.FLOAT4, 0, TANGENT_STRIDE)
            .normalized(VertexAttribute.TANGENTS)
            .build(engine)
        val box = vb.setVertices(engine, vertices)
        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        ib.setIndices(engine, indices)
        return ToonMesh(vb, ib, box)
    }

    /** Indices for n quads + t trailing triangles: [q0,q1,q2, q0,q2,q3] × n then t × [a,b,c]. */
    private fun quadStripIndices(quadCount: Int, triCount: Int = 0): List<Int> = buildList {
        for (q in 0 until quadCount) {
            val b = q * 4
            add(b + 0); add(b + 1); add(b + 2)
            add(b + 0); add(b + 2); add(b + 3)
        }
        if (triCount > 0) {
            val base = quadCount * 4
            for (t in 0 until triCount) {
                val b = base + t * 3
                add(b); add(b + 1); add(b + 2)
            }
        }
    }

    // ----------------------------------------------------------------- wedge

    private fun wedgeVertices(): List<Geometry.Vertex> {
        val bottom = Direction(y = -1f)
        val back = Direction(z = 1f)
        val slope = Direction(y = 0.70710678f, z = -0.70710678f)
        val left = Direction(x = -1f)
        val right = Direction(x = 1f)
        return listOf(
            // Quad 0 — bottom (winding → −Y).
            v(-0.5f, -0.5f, -0.5f, bottom), v(0.5f, -0.5f, -0.5f, bottom),
            v(0.5f, -0.5f, 0.5f, bottom), v(-0.5f, -0.5f, 0.5f, bottom),
            // Quad 1 — back face (→ +Z).
            v(-0.5f, -0.5f, 0.5f, back), v(0.5f, -0.5f, 0.5f, back),
            v(0.5f, 0.5f, 0.5f, back), v(-0.5f, 0.5f, 0.5f, back),
            // Quad 2 — slope (→ +Y−Z).
            v(-0.5f, 0.5f, 0.5f, slope), v(0.5f, 0.5f, 0.5f, slope),
            v(0.5f, -0.5f, -0.5f, slope), v(-0.5f, -0.5f, -0.5f, slope),
            // Tri 0 — left (→ −X).
            v(-0.5f, -0.5f, -0.5f, left), v(-0.5f, -0.5f, 0.5f, left),
            v(-0.5f, 0.5f, 0.5f, left),
            // Tri 1 — right (→ +X).
            v(0.5f, -0.5f, 0.5f, right), v(0.5f, -0.5f, -0.5f, right),
            v(0.5f, 0.5f, 0.5f, right),
        )
    }

    // --------------------------------------------------------------- pyramid

    private fun pyramidVertices(): List<Geometry.Vertex> {
        val down = Direction(y = -1f)
        val nz = normalize3(0.5f, 0.5f, -1f)
        val pz = normalize3(0.5f, 0.5f, 1f)
        val px = normalize3(1f, 0.5f, 0.5f)
        val nx = normalize3(-1f, 0.5f, 0.5f)
        val apex = Position(0f, 0.5f, 0f)
        val b0 = Position(-0.5f, -0.5f, -0.5f)
        val b1 = Position(0.5f, -0.5f, -0.5f)
        val b2 = Position(0.5f, -0.5f, 0.5f)
        val b3 = Position(-0.5f, -0.5f, 0.5f)
        return listOf(
            // Quad 0 — base (→ −Y).
            v(b0, down), v(b1, down), v(b2, down), v(b3, down),
            // Tri 0 — +Z side.
            v(b3, pz), v(b2, pz), v(apex, pz),
            // Tri 1 — −Z side.
            v(b1, nz), v(b0, nz), v(apex, nz),
            // Tri 2 — +X side.
            v(b2, px), v(b1, px), v(apex, px),
            // Tri 3 — −X side.
            v(b0, nx), v(b3, nx), v(apex, nx),
        )
    }

    // ----------------------------------------------------------------- ingot

    private fun ingotVertices(): List<Geometry.Vertex> {
        val down = Direction(y = -1f)
        val up = Direction(y = 1f)
        val pz = normalize3(0f, 0.34f, 1f)
        val nz = normalize3(0f, 0.34f, -1f)
        val px = normalize3(1f, 0.45f, 0f)
        val nx = normalize3(-1f, 0.45f, 0f)
        return listOf(
            // Bottom.
            v(-0.5f, -0.16f, -0.28f, down), v(0.5f, -0.16f, -0.28f, down),
            v(0.5f, -0.16f, 0.28f, down), v(-0.5f, -0.16f, 0.28f, down),
            // Top (smaller).
            v(-0.31f, 0.16f, -0.16f, up), v(-0.31f, 0.16f, 0.16f, up),
            v(0.31f, 0.16f, 0.16f, up), v(0.31f, 0.16f, -0.16f, up),
            // +Z long side.
            v(-0.5f, -0.16f, 0.28f, pz), v(0.5f, -0.16f, 0.28f, pz),
            v(0.31f, 0.16f, 0.16f, pz), v(-0.31f, 0.16f, 0.16f, pz),
            // −Z long side.
            v(0.5f, -0.16f, -0.28f, nz), v(-0.5f, -0.16f, -0.28f, nz),
            v(-0.31f, 0.16f, -0.16f, nz), v(0.31f, 0.16f, -0.16f, nz),
            // +X end.
            v(0.5f, -0.16f, 0.28f, px), v(0.5f, -0.16f, -0.28f, px),
            v(0.31f, 0.16f, -0.16f, px), v(0.31f, 0.16f, 0.16f, px),
            // −X end.
            v(-0.5f, -0.16f, -0.28f, nx), v(-0.5f, -0.16f, 0.28f, nx),
            v(-0.31f, 0.16f, 0.16f, nx), v(-0.31f, 0.16f, -0.16f, nx),
        )
    }

    // ------------------------------------------------------------------ rock

    /** 6 faces × (4 corners + 1 centre), jittered per variant. */
    private fun rockVertices(variant: Int): List<Geometry.Vertex> {
        val rnd = java.util.Random(1013L + variant * 977L)
        fun j(base: Float, amp: Float) = base + (rnd.nextFloat() - 0.5f) * amp

        // 8 jittered corners.
        val c = ArrayList<Position>(8)
        for (i in 0 until 8) {
            val sx = if (i and 1 == 0) -1 else 1
            val sy = if (i and 2 == 0) -1 else 1
            val sz = if (i and 4 == 0) -1 else 1
            val len = sqrt((sx * sx + sy * sy + sz * sz).toFloat())
            val r = j(0.52f, 0.2f)
            c.add(Position(sx / len * r, sy / len * r, sz / len * r))
        }
        // Corner index per face, wound so the outward normal points out.
        // Corner index = bit0 x-sign | bit1 y-sign | bit2 z-sign (0 → −, 1 → +).
        val faces = listOf(
            Face(0, intArrayOf(1, 3, 7, 5), Position(1f, 0f, 0f)),   // +X
            Face(1, intArrayOf(0, 4, 6, 2), Position(-1f, 0f, 0f)),  // −X
            Face(2, intArrayOf(2, 6, 7, 3), Position(0f, 1f, 0f)),   // +Y
            Face(3, intArrayOf(0, 1, 5, 4), Position(0f, -1f, 0f)),  // −Y
            Face(4, intArrayOf(4, 5, 7, 6), Position(0f, 0f, 1f)),   // +Z
            Face(5, intArrayOf(0, 2, 3, 1), Position(0f, 0f, -1f)),  // −Z
        )
        val out = ArrayList<Geometry.Vertex>(30)
        for (f in faces) {
            val centre = Position(
                f.centre.x * j(0.3f, 0.1f),
                f.centre.y * j(0.3f, 0.1f),
                f.centre.z * j(0.3f, 0.1f),
            )
            val q = f.corners.map { c[it] }
            // Face normal from the jittered quad (component-wise: kotlin-math's
            // inline operators target JVM 21 bytecode and can't be inlined here).
            val e1x = q[1].x - q[0].x; val e1y = q[1].y - q[0].y; val e1z = q[1].z - q[0].z
            val e2x = q[2].x - q[0].x; val e2y = q[2].y - q[0].y; val e2z = q[2].z - q[0].z
            val n = normalize3(
                e1y * e2z - e1z * e2y,
                e1z * e2x - e1x * e2z,
                e1x * e2y - e1y * e2x,
            )
            out.add(v(centre, n))
            q.forEach { out.add(v(it, n)) }
        }
        return out
    }

    private fun rockIndices(): List<Int> = buildList {
        for (f in 0 until 6) {
            val b = f * 5
            add(b); add(b + 1); add(b + 2)
            add(b); add(b + 2); add(b + 3)
            add(b); add(b + 3); add(b + 4)
            add(b); add(b + 4); add(b + 1)
        }
    }

    private data class Face(val axis: Int, val corners: IntArray, val centre: Position)

    private fun v(x: Float, y: Float, z: Float, n: Direction) =
        Geometry.Vertex(position = Position(x, y, z), normal = n)

    private fun v(p: Position, n: Direction) = Geometry.Vertex(position = p, normal = n)

    private fun normalize3(x: Float, y: Float, z: Float): Direction {
        val len = sqrt(x * x + y * y + z * z)
        return Direction(x / len, y / len, z / len)
    }

    private companion object {
        const val POSITION_STRIDE = 3 * Float.SIZE_BYTES
        const val TANGENT_STRIDE = 4 * Float.SIZE_BYTES
    }
}

/**
 * Declarative node rendering a shared [ToonMesh] with a toon material.
 * Transforms are applied once at creation — the scene never recomposes; all
 * runtime motion is written imperatively by [MineAnimator].
 */
@Composable
internal fun SceneScope.MeshPart(
    mesh: ToonMesh,
    materialInstance: MaterialInstance,
    position: Position = Position(x = 0f),
    rotation: Rotation = Rotation(x = 0f),
    scale: Scale = Scale(1f),
    apply: MeshNode.() -> Unit = {},
) {
    MeshNode(
        primitiveType = RenderableManager.PrimitiveType.TRIANGLES,
        vertexBuffer = mesh.vertexBuffer,
        indexBuffer = mesh.indexBuffer,
        boundingBox = mesh.boundingBox,
        materialInstance = materialInstance,
        apply = {
            this.position = position
            this.rotation = rotation
            this.scale = scale
            apply()
        },
    )
}
