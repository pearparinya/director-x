package com.pearparinya.automovie

internal object ChannelFeaturePolicy {
    fun hasFantasyTools(channel: String): Boolean =
        ChannelCatalog.currentChannel(channel) == ChannelCatalog.channel

    fun candidates(topics: List<String>, previous: String): List<String> =
        topics.filter { it != previous }.ifEmpty { topics }
}
