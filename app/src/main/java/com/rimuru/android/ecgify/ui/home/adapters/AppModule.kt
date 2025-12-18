package com.rimuru.android.ecgify.ui.home.adapters

import com.rimuru.android.ecgify.digitization.Digitizer
import com.rimuru.android.ecgify.digitization.model.Lead
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideDigitizer(): Digitizer {
        return Digitizer(
            layout = Pair(6, 2),
            rhythm = listOf(Lead.II),
            rpAtRight = false,
            cabrera = false,
            interpolation = null
        )
    }
}