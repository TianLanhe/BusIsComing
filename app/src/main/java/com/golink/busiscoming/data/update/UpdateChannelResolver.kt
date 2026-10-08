package com.golink.busiscoming.data.update

import com.golink.busiscoming.data.model.InitialInstallChannel

enum class UpdateChannelDecision {
    PLAY,
    PLAY_WITH_WEBSITE_METADATA,
    PLAY_FAILED,
    WEBSITE,
    PLAY_UNAVAILABLE
}

object UpdateChannelResolver {
    fun resolve(
        playAvailability: PlayStoreAvailability,
        initialInstallChannel: InitialInstallChannel,
        playResult: PlayUpdateResult?
    ): UpdateChannelDecision {
        when (playAvailability) {
            PlayStoreAvailability.MISSING, PlayStoreAvailability.DISABLED -> return UpdateChannelDecision.WEBSITE
            PlayStoreAvailability.UNUSABLE -> return UpdateChannelDecision.PLAY_UNAVAILABLE
            PlayStoreAvailability.AVAILABLE -> Unit
        }
        return when (playResult) {
            is PlayUpdateResult.Available,
            PlayUpdateResult.NotAvailable -> UpdateChannelDecision.PLAY
            PlayUpdateResult.AppNotOwned -> UpdateChannelDecision.PLAY_WITH_WEBSITE_METADATA
            is PlayUpdateResult.Failed,
            null -> UpdateChannelDecision.PLAY_FAILED
        }
    }
}

