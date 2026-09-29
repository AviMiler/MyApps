package com.myappstore.smsforwarder.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/** Stores the list columns of [Route] as JSON text. */
class Converters {

    @TypeConverter
    fun partiesToJson(parties: List<Party>): String = Json.parties(parties).toString()

    @TypeConverter
    fun jsonToParties(json: String): List<Party> = Json.parties(JSONArray(json))

    @TypeConverter
    fun wordsToJson(words: List<String>): String = JSONArray(words).toString()

    @TypeConverter
    fun jsonToWords(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { array.optString(it) }
    }
}

/** Small JSON helpers shared by the database converters and backups. */
object Json {

    fun party(party: Party): JSONObject = JSONObject().apply {
        put("address", party.address)
        party.name?.let { put("name", it) }
        if (party.contactId != 0L) put("contactId", party.contactId)
        party.photoUri?.let { put("photo", it) }
    }

    fun party(json: JSONObject): Party = Party(
        address = json.optString("address"),
        name = json.optString("name").takeIf { json.has("name") && it.isNotEmpty() },
        contactId = json.optLong("contactId", 0L),
        photoUri = json.optString("photo").takeIf { json.has("photo") && it.isNotEmpty() },
    )

    fun parties(list: List<Party>): JSONArray = JSONArray().apply { list.forEach { put(party(it)) } }

    fun parties(array: JSONArray?): List<Party> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { party(it) }?.takeIf { it.address.isNotBlank() }
        }
    }

    fun words(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }
}
