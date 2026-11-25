package com.znova.di

import com.znova.engine.sim.GameEngine
import com.znova.engine.world.PackRegistry
import com.znova.original.OriginalPack
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {
    @Provides
    @Singleton
    fun providePackRegistry(): PackRegistry = PackRegistry(listOf(OriginalPack))

    @Provides
    @Singleton
    fun provideGameEngine(registry: PackRegistry): GameEngine = registry[OriginalPack.id].engine()
}
