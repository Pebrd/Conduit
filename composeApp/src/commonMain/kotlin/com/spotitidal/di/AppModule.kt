package com.spotitidal.di

import org.koin.dsl.module
import com.spotitidal.data.sync.SyncEngine
import com.spotitidal.data.local.*

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.spotitidal.data.spotify.*
import com.spotitidal.data.tidal.*
import com.spotitidal.domain.repository.*
import com.spotitidal.domain.usecase.*

import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*

val appModule = module {
    single { 
        HttpClient {
            install(ContentNegotiation) {
                json(Json { 
                    ignoreUnknownKeys = true 
                    coerceInputValues = true
                })
            }
            
            val tokenStorage = get<TokenStorage>()
            
            // Note: This is a simplified interceptor. 
            // Real apps should use Ktor Auth plugin with RefreshToken logic.
            install(Auth) {
                bearer {
                    loadTokens {
                        // We need to know which service we are calling.
                        // This is tricky for a single client.
                        // For now, I'll just add it manually in ApiClients if needed,
                        // or detect the host.
                        null
                    }
                }
            }
        }
    }
    
    single { OAuthRepository(get()) }
    
    single { TokenStorage(get()) }
    single { BlacklistStorage(get()) }
    single { MappingStorage(get()) }
    single { HistoryStorage(get()) }
    single { SettingsStorage(get()) }
    
    single { SpotifyApiClient(get(), get()) }
    single { TidalApiClient(get(), get()) }
    
    single<SpotifyRepository> { SpotifyRepositoryImpl(get()) }
    single<TidalRepository> { TidalRepositoryImpl(get()) }
    
    single { SyncEngine(get(), get(), get()) }
    
    // Use Cases
    single { GetPlaylistsUseCase(get()) }
    single { BuildDiffUseCase(get(), get()) }
    single { SyncPlaylistUseCase(get(), get(), get()) }

    // ViewModels
    factory { SettingsViewModel(get(), get(), get()) }
    factory { HomeViewModel(get()) }
    factory { SyncViewModel(get()) }
    factory { DiffViewModel(get()) }
}
