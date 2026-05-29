package com.conduit.domain.usecase

import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.repository.TidalRepository

class HandleLikeUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val tidalRepo: TidalRepository,
) {
    suspend operator fun invoke(track: DiscoverTrack, session: DiscoverSession, onPlaylistCreated: (String) -> Unit) {
        // TODO: implement when spotifyRepo gets searchByIsrc/searchTrack/addTrackToPlaylist
    }
}

fun generatePlaylistName(seed: DiscoverSeed): String = when (seed) {
    is DiscoverSeed.FromTrack -> "Conduit: ${seed.trackName}"
    is DiscoverSeed.FromPlaylist -> seed.playlistName
}
