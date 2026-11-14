package com.mcalonzo.personalfinanceos.di

import android.content.Context
import androidx.room.Room
import com.mcalonzo.personalfinanceos.data.local.AppDatabase
import com.mcalonzo.personalfinanceos.data.local.SeedDatabaseCallback
import com.mcalonzo.personalfinanceos.data.local.dao.BudgetDao
import com.mcalonzo.personalfinanceos.data.local.dao.MotoDao
import com.mcalonzo.personalfinanceos.data.local.dao.ObligationDao
import com.mcalonzo.personalfinanceos.data.local.dao.RoutineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseProvider: Provider<AppDatabase>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "personal_finance_os.db"
        )
            .addCallback(SeedDatabaseCallback { databaseProvider.get() })
            .build()
    }

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideObligationDao(db: AppDatabase): ObligationDao = db.obligationDao()

    @Provides
    fun provideMotoDao(db: AppDatabase): MotoDao = db.motoDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
