package com.conduit.domain.usecase

import com.conduit.domain.model.*
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.repository.TidalRepository

class HandleLikeUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val tidalRepo: TidalRepository,
) {
    suspend operator fun invoke(track: DiscoverTrack, session: DiscoverSession, onPlaylistCreated: (String) -> Unit) {
        // 1. Resolve the destination playlist (create if needed)
        val playlistId = session.destination.playlistId ?: run {
            val newId = spotifyRepo.createPlaylist(session.destination.playlistName)
            onPlaylistCreated(newId)
            newId
        }

        // 2. Find the Spotify track — try ISRC first, then name+artist, then query fallback
        val spotifyTrack = track.isrc?.let { spotifyRepo.searchByIsrc(it) }
            ?: spotifyRepo.searchTrack(track.name, track.artist)
            ?: spotifyRepo.searchTracksByQuery(
                "track:\"${track.name}\" artist:\"${track.artist}\""
            ).firstOrNull()

        // 3. Add to playlist if found
        if (spotifyTrack != null) {
            spotifyRepo.addTrackToPlaylist(playlistId, spotifyTrack.id)
        }
    }
}

fun generatePlaylistName(seed: DiscoverSeed): String = when (seed) {
    is DiscoverSeed.FromTrack -> "Conduit: ${seed.trackName}"
    is DiscoverSeed.FromPlaylist -> seed.playlistName
}
