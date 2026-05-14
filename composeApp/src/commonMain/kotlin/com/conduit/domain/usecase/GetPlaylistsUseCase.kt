package com.conduit.domain.usecase

import com.conduit.domain.model.Playlist
import com.conduit.domain.repository.SpotifyRepository
import com.conduit.domain.repository.TidalRepository

class GetPlaylistsUseCase(
    private val spotifyRepo: SpotifyRepository,
    private val tidalRepo: TidalRepository
) {
    suspend operator fun invoke(): List<Playlist> {
        val spotifyPlaylists = try { 
            spotifyRepo.getPlaylists() 
        } catch (e: Exception) { 
            emptyList() 
        }
        
        val tidalPlaylists = try { 
            tidalRepo.getPlaylists() 
        } catch (e: Exception) { 
            emptyList() 
        }
        
        return spotifyPlaylists + tidalPlaylists
    }
}
