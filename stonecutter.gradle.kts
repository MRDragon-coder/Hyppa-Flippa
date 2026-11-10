import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.time.LocalDate
import java.util.zip.ZipInputStream

plugins {
    id("dev.kikugie.stonecutter")
}

// The Minecraft version src/ currently compiles against (what IntelliJ shows you).
// Switch it with the "Set active project to ..." Gradle tasks, not by editing this line.
stonecutter active "26.2"

// --- `./gradlew updateRecipes`: refreshes the crafting recipes the Craft tab uses.
//
// Hypixel's API has no recipes, so they come from the NotEnoughUpdates repo (MIT licensed), the
// item database most SkyBlock mods share. This downloads it (~22 MB), keeps only what the mod
// needs and writes src/main/resources/assets/hypaflippa/recipes.json (plus NEU's license next to
// it), which is committed and packed into the jar. Builds never touch the network; run this before a release instead.

val neuRepo = "NotEnoughUpdates/NotEnoughUpdates-REPO"

tasks.register("updateRecipes") {
    group = "hypaflippa"
    description = "Downloads the NotEnoughUpdates repo and rewrites the bundled recipes.json"
    val output = layout.projectDirectory.file("src/main/resources/assets/hypaflippa/recipes.json").asFile

    doLast {
        val slurper = JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val commit = (slurper.parse(URI("https://api.github.com/repos/$neuRepo/commits/master").toURL()) as Map<String, Any?>)["sha"] as String

        // Every items/<ID>.json in the zip, parsed, plus the repo's license, which has to ship
        // with the data it covers.
        val items = sortedMapOf<String, Map<String, Any?>>()
        var license: String? = null
        var sacksJson: Any? = null
        ZipInputStream(URI("https://codeload.github.com/$neuRepo/zip/$commit").toURL().openStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (Regex("^[^/]+/LICENSE$").matches(entry.name)) license = zip.readBytes().decodeToString()
                if (Regex("^[^/]+/constants/sacks\\.json$").matches(entry.name)) sacksJson = slurper.parse(zip.readBytes())
                val match = Regex("^[^/]+/items/(.+)\\.json$").find(entry.name) ?: continue
                @Suppress("UNCHECKED_CAST")
                items[match.groupValues[1]] = slurper.parse(zip.readBytes()) as Map<String, Any?>
            }
        }
        check(items.size > 1000) { "Only ${items.size} items in the NEU repo, something is wrong" }
        checkNotNull(license) { "The NEU repo has no LICENSE file any more; check its terms before bundling it" }

        // NEU writes enchanted books and pets as NAME;LEVEL. The mod prices them under the
        // bazaar's ENCHANTMENT_NAME_LEVEL and the Auction House scan's PET:NAME:RARITY.
        val rarities = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
        fun priceKey(neuId: String): String {
            // Vanilla variants: NEU writes INK_SACK-3 (cocoa beans), the bazaar INK_SACK:3.
            if (Regex("^[A-Z_]+-\\d+$").matches(neuId)) return neuId.replace('-', ':')
            val parts = neuId.split(";")
            if (parts.size != 2) return neuId
            val item = items[neuId] ?: return neuId
            if (item["itemid"] == "minecraft:enchanted_book") return "ENCHANTMENT_${parts[0]}_${parts[1]}"
            val level = parts[1].toIntOrNull()
            if ((item["displayname"] as? String)?.contains("[Lvl") == true && level != null && level in rarities.indices) {
                return "PET:${parts[0]}:${rarities[level]}"
            }
            return neuId
        }

        /** "ENCHANTED_DIAMOND:32" -> (ENCHANTED_DIAMOND, 32); a missing count means 1. */
        fun stack(text: String): Pair<String, Double> {
            val colon = text.lastIndexOf(':')
            if (colon < 0) return text to 1.0
            return text.substring(0, colon) to (text.substring(colon + 1).toDoubleOrNull() ?: 1.0)
        }

        val names = sortedMapOf<String, String>()
        val roman = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
        fun words(id: String) = id.lowercase().split("_").filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        fun remember(neuId: String): String {
            val key = priceKey(neuId)
            val name = (items[neuId]?.get("displayname") as? String)?.replace(Regex("§."), "")?.trim()
            val parts = neuId.split(";")
            names[key] = when {
                // Every book is called "Enchanted Book" in NEU: name it after the enchantment.
                parts.size == 2 && key.startsWith("ENCHANTMENT_") -> words(parts[0]) + " " + (roman.getOrNull(parts[1].toInt()) ?: parts[1])
                // "[Lvl {LVL}] Bat" -> "Bat (Common)"
                parts.size == 2 && key.startsWith("PET:") -> (name ?: words(parts[0])).replace("[Lvl {LVL}]", "").trim() + " (" + words(key.substringAfterLast(':')) + ")"
                name != null -> name
                else -> return key
            }
            return key
        }

        val slots = listOf("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")
        val recipes = mutableListOf<Map<String, Any>>()
        val npcShop = sortedMapOf<String, Double>()
        for ((id, item) in items) {
            @Suppress("UNCHECKED_CAST")
            val grids = buildList {
                (item["recipe"] as? Map<String, Any?>)?.let(::add)
                (item["recipes"] as? List<Map<String, Any?>>)?.filter { it["type"] == "crafting" }?.forEach(::add)
            }
            for (grid in grids) {
                val inputs = sortedMapOf<String, Int>()
                for (slot in slots) {
                    val text = grid[slot] as? String
                    if (text.isNullOrEmpty()) continue
                    val (inputId, count) = stack(text)
                    inputs.merge(remember(inputId), count.toInt(), Int::plus)
                }
                if (inputs.isEmpty()) continue
                val outputId = remember(grid["overrideOutputId"] as? String ?: id)
                val recipe = linkedMapOf<String, Any>(
                    "id" to outputId,
                    "count" to ((grid["count"] as? Number)?.toInt() ?: 1),
                    "inputs" to inputs,
                )
                // "Requires: Diamond VIII" -> "Diamond VIII"
                val requires = (item["crafttext"] as? String)?.removePrefix("Requires:")?.trim()
                if (!requires.isNullOrEmpty()) recipe["requires"] = requires
                if (recipe !in recipes) recipes.add(recipe)
            }

            // NPC shops that sell for coins alone: the cheapest coin price per item, so plain
            // ingredients like sticks get a price even though no player market has them.
            @Suppress("UNCHECKED_CAST")
            for (shop in (item["recipes"] as? List<Map<String, Any?>>).orEmpty().filter { it["type"] == "npc_shop" }) {
                val cost = (shop["cost"] as? List<String>)?.singleOrNull()?.let(::stack) ?: continue
                if (cost.first != "SKYBLOCK_COIN") continue
                val (resultId, resultCount) = stack(shop["result"] as? String ?: continue)
                if (resultCount <= 0) continue
                npcShop.merge(remember(resultId), cost.second / resultCount, ::minOf)
            }
        }

        // Everything any sack holds: when every ingredient is in your sacks you can craft straight
        // from them, any amount at once, which makes mass crafting easy.
        @Suppress("UNCHECKED_CAST")
        val sacks = ((sacksJson as? Map<String, Any?>)?.get("sacks") as? Map<String, Map<String, Any?>>)
            ?: error("The NEU repo has no constants/sacks.json in the expected shape any more")
        @Suppress("UNCHECKED_CAST")
        val sackable = sacks.values.flatMap { (it["contents"] as? List<String>).orEmpty() }.map(::priceKey).toSortedSet()

        // One recipe per line keeps the diffs of later updates readable.
        val text = buildString {
            append("{\n")
            append("  \"source\": ${JsonOutput.toJson("https://github.com/$neuRepo")},\n")
            append("  \"commit\": ${JsonOutput.toJson(commit)},\n")
            append("  \"updated\": ${JsonOutput.toJson(LocalDate.now().toString())},\n")
            append("  \"recipes\": [\n")
            append(recipes.joinToString(",\n") { "    " + JsonOutput.toJson(it) })
            append("\n  ],\n")
            append("  \"npcShop\": ${JsonOutput.toJson(npcShop)},\n")
            append("  \"sackable\": ${JsonOutput.toJson(sackable)},\n")
            append("  \"names\": ${JsonOutput.toJson(names)}\n")
            append("}\n")
        }
        output.parentFile.mkdirs()
        output.writeText(text)
        output.resolveSibling("recipes-LICENSE.txt").writeText(
            "recipes.json is made from the NotEnoughUpdates repo (https://github.com/$neuRepo),\n" +
            "commit $commit, under this license:\n\n" + license)
        println("Wrote ${recipes.size} recipes, ${npcShop.size} NPC shop prices, ${sackable.size} sack items from NEU commit ${commit.take(7)} to $output")
    }
}
