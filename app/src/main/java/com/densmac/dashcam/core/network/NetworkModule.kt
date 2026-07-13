package com.densmac.dashcam.core.network

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.work.WorkManager
import com.densmac.dashcam.BuildConfig
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.DefaultDispatchersProvider
import com.densmac.dashcam.core.common.DispatchersProvider
import com.densmac.dashcam.core.player.LivePreviewEngine
import com.densmac.dashcam.core.player.RtspLivePreviewController
import com.densmac.dashcam.data.api.DashcamApi
import com.densmac.dashcam.data.db.DashcamDatabase
import com.densmac.dashcam.data.db.dao.DeviceDao
import com.densmac.dashcam.data.db.dao.DownloadDao
import com.densmac.dashcam.data.repository.DashcamRepositoryImpl
import com.densmac.dashcam.data.repository.DownloadRepositoryImpl
import com.densmac.dashcam.data.repository.FileRepositoryImpl
import com.densmac.dashcam.data.repository.SettingsRepositoryImpl
import com.densmac.dashcam.domain.repository.DashcamRepository
import com.densmac.dashcam.domain.repository.DownloadRepository
import com.densmac.dashcam.domain.repository.FileRepository
import com.densmac.dashcam.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds abstract fun bindDispatchers(impl: DefaultDispatchersProvider): DispatchersProvider
    @Binds abstract fun bindNetworkBinder(impl: DashcamNetworkBinderImpl): DashcamNetworkBinder
    @Binds abstract fun bindConnectionMonitor(impl: DashcamConnectionMonitorImpl): DashcamConnectionMonitor
    @Binds abstract fun bindDashcamRepository(impl: DashcamRepositoryImpl): DashcamRepository
    @Binds abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository
    @Binds abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
    @Binds abstract fun bindLivePreviewEngine(impl: RtspLivePreviewController): LivePreviewEngine
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager =
        requireNotNull(context.getSystemService<ConnectivityManager>())

    @Provides
    @Singleton
    @ApiOkHttpClient
    fun provideApiOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(DashcamConstants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DashcamConstants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DashcamConstants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    @DownloadOkHttpClient
    fun provideDownloadOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(DashcamConstants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DashcamConstants.DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DashcamConstants.DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(@ApiOkHttpClient client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(DashcamConstants.HTTP_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideDashcamApi(retrofit: Retrofit): DashcamApi = retrofit.create(DashcamApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DashcamDatabase =
        Room.databaseBuilder(context, DashcamDatabase::class.java, "dashcam.db")
            .fallbackToDestructiveMigration(false)
            .build()

    @Provides fun provideDownloadDao(database: DashcamDatabase): DownloadDao = database.downloadDao()
    @Provides fun provideDeviceDao(database: DashcamDatabase): DeviceDao = database.deviceDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
