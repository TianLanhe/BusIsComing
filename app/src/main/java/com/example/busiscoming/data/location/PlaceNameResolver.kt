package com.example.busiscoming.data.location

import com.example.busiscoming.data.model.Place

interface PlaceNameResolver {
    fun resolve(snapshot: CurrentLocationSnapshot): Place?
}

object MockPlaceNameResolver : PlaceNameResolver {
    const val MOCK_PLACE_NAME = "目前位置附近"

    override fun resolve(snapshot: CurrentLocationSnapshot): Place {
        return Place(
            name = MOCK_PLACE_NAME,
            latitude = snapshot.latitude,
            longitude = snapshot.longitude
        )
    }
}
