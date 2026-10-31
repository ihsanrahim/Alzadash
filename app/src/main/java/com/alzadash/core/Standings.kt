package com.alzadash.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One standings table: column names and rows of text, first column is the position. */
data class Table(val title: String, val columns: List<String>, val rows: List<List<String>>)

/**
 * Championship standings for the Sports tab: F1 (Jolpica, the Ergast successor),
 * football (ESPN) and MotoGP (the official results API). Cached for 30 minutes.
 */
object Standings {
    const val F1 = "f1"
    const val MOTOGP = "motogp"
    const val EPL = "soccer:eng.1"
    const val LIGA_M = "soccer:mys.1"

    private class Entry(val tables: List<Table>, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newSingleThreadExecutor()

    /** Tables for [kind]; null until the first fetch ends, empty if it failed. */
    fun get(kind: String): List<Table>? {
        val e = cache[kind]
        if ((e == null || System.currentTimeMillis() - e.at > 30 * 60_000) && pending.add(kind)) {
            pool.execute {
                val tables = try { load(kind) } catch (_: Exception) { emptyList() }
                if (tables.isNotEmpty() || e == null) cache[kind] = Entry(tables, System.currentTimeMillis())
                pending.remove(kind)
            }
        }
        return e?.tables
    }

    private fun load(kind: String): List<Table> = when {
        kind == F1 -> f1()
        kind == MOTOGP -> motoGp()
        kind.startsWith("soccer:") -> football(kind.removePrefix("soccer:"))
        else -> emptyList()
    }

    private fun f1(): List<Table> {
        val base = "https://api.jolpi.ca/ergast/f1/current"
        val drivers = list(json(base + "/driverStandings.json"), "DriverStandings").map { d ->
            val driver = d.getJSONObject("Driver")
            val team = d.optJSONArray("Constructors")?.optJSONObject(0)?.optString("name") ?: ""
            listOf(d.optString("position", "-"), driver.optString("familyName"), team, d.optString("wins"), d.optString("points"))
        }
        val teams = list(json(base + "/constructorStandings.json"), "ConstructorStandings").map { c ->
            listOf(c.optString("position", "-"), c.getJSONObject("Constructor").optString("name"), c.optString("wins"), c.optString("points"))
        }
        return listOfNotNull(
            if (drivers.isEmpty()) null else Table("DRIVERS", listOf("#", "Driver", "Team", "Wins", "Pts"), drivers),
            if (teams.isEmpty()) null else Table("TEAMS", listOf("#", "Team", "Wins", "Pts"), teams),
        )
    }

    private fun list(root: JSONObject, key: String): List<JSONObject> {
        val lists = root.getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists")
        if (lists.length() == 0) return emptyList()
        return objects(lists.getJSONObject(0).getJSONArray(key))
    }

    private fun football(league: String): List<Table> {
        val root = json("https://site.api.espn.com/apis/v2/sports/soccer/$league/standings")
        val groups = root.optJSONArray("children")
        val sources = if (groups != null && groups.length() > 0) objects(groups) else listOf(root)
        return sources.mapNotNull { g ->
            val entries = g.optJSONObject("standings")?.optJSONArray("entries") ?: return@mapNotNull null
            val rows = objects(entries).map { e ->
                val stats = objects(e.optJSONArray("stats") ?: JSONArray()).associate { it.optString("name") to it.optDouble("value") }
                fun n(k: String) = stats[k]?.takeIf { !it.isNaN() }?.toInt()?.toString() ?: "-"
                val team = e.optJSONObject("team")
                val name = team?.optString("shortDisplayName")?.takeIf { it.isNotEmpty() } ?: team?.optString("displayName") ?: ""
                val gd = stats["pointDifferential"]?.takeIf { !it.isNaN() }?.toInt()
                listOf(n("rank"), name, n("gamesPlayed"), n("wins"), n("ties"), n("losses"),
                    if (gd == null) "-" else if (gd > 0) "+$gd" else "$gd", n("points"))
            }.sortedBy { it[0].toIntOrNull() ?: 99 }
            if (rows.isEmpty()) null else Table(g.optString("name").uppercase(), listOf("#", "Team", "P", "W", "D", "L", "GD", "Pts"), rows)
        }
    }

    private fun motoGp(): List<Table> {
        val api = "https://api.motogp.pulselive.com/motogp/v1/results"
        val seasons = objects(JSONArray(text("$api/seasons")))
        val season = seasons.firstOrNull { it.optBoolean("current") } ?: seasons.maxByOrNull { it.optInt("year") } ?: return emptyList()
        val sid = season.getString("id")
        val cats = objects(JSONArray(text("$api/categories?seasonUuid=$sid")))
        val cat = cats.firstOrNull { it.optInt("legacy_id") == 3 } ?: cats.firstOrNull { it.optString("name").startsWith("MotoGP") } ?: return emptyList()
        val rows = objects(json("$api/standings?seasonUuid=$sid&categoryUuid=${cat.getString("id")}").getJSONArray("classification")).map { c ->
            val rider = c.optJSONObject("rider")?.optString("full_name") ?: ""
            val bike = c.optJSONObject("constructor")?.optString("name") ?: c.optJSONObject("team")?.optString("name") ?: ""
            listOf(c.opt("position")?.toString() ?: "-", rider, bike, c.opt("race_wins")?.toString() ?: "-", c.opt("points")?.toString() ?: "-")
        }
        return if (rows.isEmpty()) emptyList() else listOf(Table("MOTOGP ${season.optInt("year")}", listOf("#", "Rider", "Bike", "Wins", "Pts"), rows))
    }

    private fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    private fun json(url: String) = JSONObject(text(url))

    private fun text(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "AlzaDash")
        c.setRequestProperty("Accept", "application/json")
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
