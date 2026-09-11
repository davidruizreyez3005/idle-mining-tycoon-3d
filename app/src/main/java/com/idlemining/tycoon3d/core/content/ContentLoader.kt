package com.idlemining.tycoon3d.core.content

import kotlinx.serialization.json.Json

/**
 * Thrown when a gamedata JSON file is structurally invalid or references content
 * that does not exist. Content errors must fail loudly at load time — never at
 * gameplay time.
 */
class ContentException(message: String) : Exception(message)

/**
 * Pure, platform-free parser + validator for the gamedata JSON bundle.
 *
 * The Android side reads the five files from `assets/gamedata/` into a
 * `Map<String, String>` and hands them here; unit tests construct the same map
 * from literals. Everything downstream (economy, simulation, world building)
 * only ever sees the validated [GameContent].
 */
object ContentLoader {

    val FILE_RESOURCES = "resources.json"
    val FILE_NODES = "mine_nodes.json"
    val FILE_UPGRADES = "upgrades.json"
    val FILE_ECONOMY = "economy.json"
    val FILE_WORLD = "world.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun load(files: Map<String, String>): GameContent {
        val resources = decode<ResourcesFile>(files, FILE_RESOURCES).let(::validateResources)
        val nodeTypes = decode<MineNodesFile>(files, FILE_NODES).let(::validateNodes)
        val upgrades = decode<UpgradesFile>(files, FILE_UPGRADES).let(::validateUpgrades)
        val economy = decode<EconomyFile>(files, FILE_ECONOMY).let(::validateEconomy)
        val world = decode<WorldFile>(files, FILE_WORLD).let { validateWorld(it, nodeTypes) }

        return GameContent(
            resources = resources.associateBy { it.id },
            resourceOrder = resources.map { it.id },
            nodeTypes = nodeTypes.associateBy { it.id },
            upgrades = upgrades.associateBy { it.id },
            upgradeOrder = upgrades.map { it.id },
            economy = economy,
            world = world,
        )
    }

    private inline fun <reified T> decode(files: Map<String, String>, name: String): T {
        val raw = files[name] ?: throw ContentException("Missing gamedata file: $name")
        return try {
            json.decodeFromString<T>(raw)
        } catch (e: Exception) {
            throw ContentException("Failed to parse $name: ${e.message}")
        }
    }

    // ------------------------------------------------------------- validation

    private fun validateResources(file: ResourcesFile): List<ResourceDef> {
        if (file.resources.isEmpty()) throw ContentException("resources.json: no resources defined")
        val ids = mutableSetOf<String>()
        for (r in file.resources) {
            if (r.id.isBlank()) throw ContentException("resources.json: blank resource id")
            if (!ids.add(r.id)) throw ContentException("resources.json: duplicate resource id '${r.id}'")
            if (r.baseValue < 0) throw ContentException("resources.json: '${r.id}' has negative baseValue")
            parseColor(r.color, "resource '${r.id}'")
        }
        return file.resources
    }

    private fun validateNodes(file: MineNodesFile): List<NodeTypeDef> {
        if (file.nodeTypes.isEmpty()) throw ContentException("mine_nodes.json: no node types defined")
        val ids = mutableSetOf<String>()
        for (n in file.nodeTypes) {
            if (!ids.add(n.id)) throw ContentException("mine_nodes.json: duplicate node id '${n.id}'")
            if (n.hp <= 0) throw ContentException("mine_nodes.json: '${n.id}' has hp <= 0")
            if (n.respawnSeconds < 0) throw ContentException("mine_nodes.json: '${n.id}' has negative respawn")
            if (n.yields.isEmpty()) throw ContentException("mine_nodes.json: '${n.id}' yields nothing")
            n.yields.forEach { (res, count) ->
                if (count <= 0) throw ContentException("mine_nodes.json: '${n.id}' yields $count x $res")
                if (res.isBlank()) throw ContentException("mine_nodes.json: '${n.id}' has blank yield resource")
            }
        }
        return file.nodeTypes
    }

    private fun validateUpgrades(file: UpgradesFile): List<UpgradeDef> {
        if (file.upgrades.isEmpty()) throw ContentException("upgrades.json: no upgrades defined")
        val ids = mutableSetOf<String>()
        for (u in file.upgrades) {
            if (!ids.add(u.id)) throw ContentException("upgrades.json: duplicate id '${u.id}'")
            if (u.baseCost < 0) throw ContentException("upgrades.json: '${u.id}' has negative baseCost")
            if (u.costGrowth <= 1.0f) throw ContentException("upgrades.json: '${u.id}' costGrowth must exceed 1.0")
            if (u.maxLevel < 1) throw ContentException("upgrades.json: '${u.id}' maxLevel must be at least 1")
            val known = setOf("miningSpeed", "moveSpeed", "backpack", "sellMargin", "idleExtraction")
            if (u.effect.type !in known) {
                throw ContentException("upgrades.json: '${u.id}' unknown effect type '${u.effect.type}'")
            }
            if (u.effect.type == "idleExtraction" && u.effect.rates.isEmpty()) {
                throw ContentException("upgrades.json: '${u.id}' idleExtraction has no rates")
            }
        }
        return file.upgrades
    }

    private fun validateEconomy(file: EconomyFile): EconomyFile {
        if (file.start.backpack < 1) throw ContentException("economy.json: start backpack must be at least 1")
        if (file.worker.moveSpeed <= 0f) throw ContentException("economy.json: worker moveSpeed must be positive")
        if (file.worker.mineDps <= 0f) throw ContentException("economy.json: worker mineDps must be positive")
        if (file.idle.offlineCapHours < 1) throw ContentException("economy.json: offlineCapHours must be at least 1")
        return file
    }

    private fun validateWorld(file: WorldFile, nodeTypes: List<NodeTypeDef>): WorldFile {
        val knownTypes = nodeTypes.map { it.id }.toSet()
        for (n in file.nodes) {
            if (n.typeId !in knownTypes) {
                throw ContentException("world.json: node references unknown typeId '${n.typeId}'")
            }
            if (n.at.size != 2) throw ContentException("world.json: node '${n.typeId}' at[] must be [x, z]")
        }
        for (p in file.props) {
            if (p.at.size != 2) throw ContentException("world.json: prop '${p.piece}' at[] must be [x, z]")
        }
        if (file.spawn.size != 2) throw ContentException("world.json: spawn must be [x, z]")
        if (file.depot.position.size != 2) throw ContentException("world.json: depot.position must be [x, z]")
        parseColor(file.ground.grassColor, "ground.grassColor")
        parseColor(file.ground.dirtColor, "ground.dirtColor")
        parseColor(file.ground.rockColor, "ground.rockColor")
        parseColor(file.cliff.wallColor, "cliff.wallColor")
        return file
    }

    /** Parses "#RRGGBB" into an opaque ARGB long. */
    fun parseColor(hex: String, what: String): Long {
        val s = hex.removePrefix("#")
        if (s.length != 6 || s.any { !it.isHexDigit() }) {
            throw ContentException("Invalid color '$hex' for $what (expected #RRGGBB)")
        }
        return 0xFF000000L or s.toLong(16)
    }
}

/** Thrown/validated aggregate passed to the game. */
class GameContent(
    val resources: Map<String, ResourceDef>,
    val resourceOrder: List<String>,
    val nodeTypes: Map<String, NodeTypeDef>,
    val upgrades: Map<String, UpgradeDef>,
    val upgradeOrder: List<String>,
    val economy: EconomyFile,
    val world: WorldFile,
) {
    fun resource(id: String): ResourceDef =
        resources[id] ?: throw ContentException("Unknown resource id '$id'")

    fun nodeType(id: String): NodeTypeDef =
        nodeTypes[id] ?: throw ContentException("Unknown node type '$id'")

    fun upgrade(id: String): UpgradeDef =
        upgrades[id] ?: throw ContentException("Unknown upgrade id '$id'")

    /** Parsed ARGB colors (ground / cliff). */
    val grassArgb: Long by lazy { ContentLoader.parseColor(world.ground.grassColor, "ground.grassColor") }
    val dirtArgb: Long by lazy { ContentLoader.parseColor(world.ground.dirtColor, "ground.dirtColor") }
    val rockArgb: Long by lazy { ContentLoader.parseColor(world.ground.rockColor, "ground.rockColor") }
    val cliffArgb: Long by lazy { ContentLoader.parseColor(world.cliff.wallColor, "cliff.wallColor") }

    fun resourceArgb(id: String): Long = resource(id).let {
        ContentLoader.parseColor(it.color, "resource '${it.id}'")
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
