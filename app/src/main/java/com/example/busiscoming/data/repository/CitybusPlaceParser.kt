package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.Place

class CitybusPlaceParseException(message: String) : IllegalArgumentException(message)

object CitybusPlaceParser {
    fun parse(response: String): List<Place> {
        val lines = response
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size < 2) {
            throw CitybusPlaceParseException("Citybus place response is empty or incomplete")
        }

        val bodyLines = lines.drop(1)
        if (bodyLines.size == 1 && bodyLines.first() == NO_RESULT) {
            return emptyList()
        }

        val places = bodyLines.mapNotNull { line -> parsePlaceLine(line) }
        if (places.isEmpty()) {
            throw CitybusPlaceParseException("Citybus place response has no valid place rows")
        }
        return places
    }

    private fun parsePlaceLine(line: String): Place? {
        val columns = line.split("|")
        if (columns.size < 4) return null

        val name = columns[1].trim()
        val latitude = columns[2].trim().toDoubleOrNull()
        val longitude = columns[3].trim().toDoubleOrNull()
        if (name.isBlank() || latitude == null || longitude == null) return null

        return Place(
            name = name,
            latitude = latitude,
            longitude = longitude
        )
    }

    private const val NO_RESULT = "No Result"
}
