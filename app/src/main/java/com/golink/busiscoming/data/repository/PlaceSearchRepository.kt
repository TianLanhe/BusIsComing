package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.Place

interface PlaceSearchRepository {
    fun searchPlaces(keyword: String): List<Place>
}
