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
        playPackageAvailable: Boolean,
        initialInstallChannel: InitialInstallChannel,
        playResult: PlayUpdateResult?
    ): UpdateChannelDecision {
        if (!playPackageAvailable) {
            return if (initialInstallChannel == InitialInstallChannel.PLAY) {
                UpdateChannelDecision.PLAY_UNAVAILABLE
            } else {
                UpdateChannelDecision.WEBSITE
            }
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

