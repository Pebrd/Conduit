package com.conduit.domain.usecase

import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.repository.TidalRepository

class HandleLikeUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val tidalRepo: TidalRepository,
) {
    suspend fun invoke(track: DiscoverTrack, session: DiscoverSession, onPlaylistCreated: (String) -> Unit) {
        val dest = session.destination

        // NONE = don't save likes anywhere
        if (dest.platform == MusicService.NONE) return

        val targetPlaylistId = if (dest.playlistId == null) {
            when (dest.platform) {
                MusicService.SPOTIFY -> spotifyRepo.createPlaylist(dest.playlistName)
                MusicService.TIDAL -> tidalRepo.createPlaylist(dest.playlistName, "Creada por Conduit Discover")
                MusicService.NONE -> return
            }.also { newId -> onPlaylistCreated(newId) }
        } else {
            dest.playlistId
        }

        when (dest.platform) {
            MusicService.SPOTIFY -> {
                val spotifyTrack = track.isrc?.let { spotifyRepo.searchByIsrc(it) }
                    ?: spotifyRepo.searchTrack(track.name, track.artist)
                spotifyTrack?.let { spotifyRepo.addTrackToPlaylist(targetPlaylistId, it.id) }
            }
            MusicService.TIDAL -> {
                val tidalTrack = track.isrc?.let { tidalRepo.searchByIsrc(it) }
                    ?: tidalRepo.searchTracks("${track.name} ${track.artist}").firstOrNull()
                tidalTrack?.let { tidalRepo.addTracksToPlaylist(targetPlaylistId, listOf(it.id)) }
            }
            MusicService.NONE -> {}
        }
    }
}

fun generatePlaylistName(seed: DiscoverSeed): String = when (seed) {
    is DiscoverSeed.FromTrack -> "Conduit: ${seed.trackName}"
    is DiscoverSeed.FromPlaylist -> seed.playlistName
}
