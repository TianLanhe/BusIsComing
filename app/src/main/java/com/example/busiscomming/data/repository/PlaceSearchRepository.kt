package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.Place

interface PlaceSearchRepository {
    fun searchPlaces(keyword: String): List<Place>
}
