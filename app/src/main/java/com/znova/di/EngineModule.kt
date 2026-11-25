package com.znova.di

import com.znova.content.BaseContent
import com.znova.engine.content.contentPack
import com.znova.engine.sim.GameEngine
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
    fun provideGameEngine(): GameEngine {
        val content = contentPack(BaseContent)

        val errors = content.validate()
        require(errors.isEmpty()) {
            "Invalid content:\n${errors.joinToString("\n")}"
        }

        return GameEngine(content)
    }
}
