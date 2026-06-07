package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.Place

interface PlaceSearchRepository {
    fun searchPlaces(keyword: String): List<Place>
}
