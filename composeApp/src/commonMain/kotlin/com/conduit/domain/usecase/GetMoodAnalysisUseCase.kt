package com.conduit.domain.usecase

import com.conduit.domain.model.MoodAnalysis
import com.conduit.domain.model.TopTrackItem
import com.conduit.domain.repository.SpotifyRepository

class GetMoodAnalysisUseCase(
    private val spotifyRepository: SpotifyRepository
) {
    suspend operator fun invoke(timeRange: String = "medium_term"): MoodAnalysis {
        val tracks = spotifyRepository.getTopTracks(timeRange, 20)
        if (tracks.isEmpty()) return MoodAnalysis(0.0, 0.0, 0.0, 0.0, 0.0)

        val trackIds = tracks.map { it.id }
        val features = spotifyRepository.getAudioFeatures(trackIds)
        if (features.isEmpty()) return MoodAnalysis(0.0, 0.0, 0.0, 0.0, 0.0)

        val count = features.size.toDouble()
        return MoodAnalysis(
            averageValence = features.sumOf { it.valence } / count,
            averageEnergy = features.sumOf { it.energy } / count,
            averageDanceability = features.sumOf { it.danceability } / count,
            averageTempo = features.sumOf { it.tempo } / count,
            averageAcousticness = features.sumOf { it.acousticness } / count,
        )
    }
}
