package com.conduit.di

import org.koin.dsl.module
import com.conduit.Credentials
import com.conduit.data.sync.SyncEngine
import com.conduit.data.local.*

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.conduit.data.spotify.*
import com.conduit.data.tidal.*
import com.conduit.data.auth.*
import com.conduit.ui.settings.*
import com.conduit.ui.home.*
import com.conduit.ui.sync.*
import com.conduit.ui.diff.*
import com.conduit.ui.auth.*
import com.conduit.domain.repository.*
import com.conduit.domain.usecase.*

import io.ktor.client.request.*
import io.ktor.http.*
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.factoryOf

import com.conduit.data.http.TokenRefreshPlugin

val appModule = module {
    single { 
        HttpClient {
            install(ContentNegotiation) {
                json(Json { 
                    ignoreUnknownKeys = true 
                    coerceInputValues = true
                })
            }
        }
    }
    
    single { OAuthRepository(get()) }
    
    single { TokenStorage(get()) }
    single<com.conduit.data.http.TokenStorage> { get<TokenStorage>() }
    
    single { BlacklistStorage(get()) }
    single { MappingStorage(get()) }
    single { HistoryStorage(get()) }
    single { SettingsStorage(get()) }

    single { 
        TokenRefreshPlugin(
            tokenStorage = get(),
            oAuthRepository = get(),
            settingsStorage = get()
        )
    }
    
    single { SpotifyApiClient(get(), get()) }
    single { TidalApiClient(get(), get(), get()) }
    
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
    viewModelOf(::AuthViewModel)
}
