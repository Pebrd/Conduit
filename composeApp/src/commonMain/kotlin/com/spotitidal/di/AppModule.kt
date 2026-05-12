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
import com.spotitidal.data.auth.*
import com.spotitidal.ui.settings.*
import com.spotitidal.ui.home.*
import com.spotitidal.ui.sync.*
import com.spotitidal.ui.diff.*
import com.spotitidal.domain.repository.*
import com.spotitidal.domain.usecase.*

import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.factoryOf

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
            
            install(Auth) {
                bearer {
                    loadTokens {
                        null as BearerTokens?
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
    factoryOf(::GetPlaylistsUseCase)
    factoryOf(::BuildDiffUseCase)
    factoryOf(::SyncPlaylistUseCase)

    // ViewModels
    viewModelOf(::SettingsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SyncViewModel)
    viewModelOf(::DiffViewModel)
}
