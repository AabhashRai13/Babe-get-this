package com.babegetthis.android.testing

import android.content.Context
import androidx.room.Room
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.data.RegisterResult
import com.babegetthis.android.core.data.di.DatabaseModule
import com.babegetthis.android.core.data.di.SupabaseModule
import com.babegetthis.android.core.data.di.VoiceModule
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.data.local.DEFAULT_CATEGORIES
import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.voice.data.repository.VoiceRepository
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// --- database ---

// In-memory, so each test process starts clean and nothing survives to the next
// run. The default categories are seeded synchronously rather than through the
// production onCreate callback, which fires them on a detached IO scope — a race
// no test should have to wait out.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    ): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .build()
        .also { db ->
            db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
            runBlocking { db.categoryDao().insertAll(DEFAULT_CATEGORIES) }
        }

    @Provides
    fun provideShoppingListDao(db: AppDatabase): ShoppingListDao = db.shoppingListDao()

    @Provides
    fun provideShoppingItemDao(db: AppDatabase): ShoppingItemDao = db.shoppingItemDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
}

// --- auth ---

// Replaces whichever AuthModule the flavour binds. The dev flavour already ships
// a FakeAuthRepository, but it sleeps 800ms per call to simulate latency — fine
// for hand-testing, pure cost in a suite. This one is instant and lets a test
// choose the outcome.
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.babegetthis.android.core.data.di.AuthModule::class],
)
object TestAuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(authStateManager: AuthStateManager): AuthRepository =
        TestAuthRepository(authStateManager)
}

// Signs in locally against AuthStateManager without any network. `failWith` lets
// a test drive the failure paths.
@Singleton
class TestAuthRepository @Inject constructor(
    private val authStateManager: AuthStateManager,
) : AuthRepository {

    @Volatile
    var failWith: AppError? = null

    private fun <T> guarded(block: () -> T): Result<T> =
        failWith?.let { Result.Error(it) } ?: Result.Success(block())

    private fun signIn(email: String, name: String): User {
        authStateManager.login(
            token = "test-token",
            userId = "test-user",
            userName = name,
            userEmail = email,
        )
        return User(id = "test-user", email = email, name = name)
    }

    override suspend fun register(email: String, password: String, name: String) =
        guarded { RegisterResult.SignedIn(signIn(email, name)) }

    override suspend fun login(email: String, password: String) =
        guarded { signIn(email, email.substringBefore("@")) }

    override suspend fun logout(): Result<Unit> {
        authStateManager.logout()
        return Result.Success(Unit)
    }

    override suspend fun updateUserName(name: String) = guarded {
        authStateManager.updateName(name)
        User(id = "test-user", email = authStateManager.currentEmail() ?: "", name = name)
    }

    override suspend fun requestPasswordReset(email: String) = guarded { }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        guarded { signIn(email, email.substringBefore("@")) }

    override suspend fun deleteAccount() = guarded { authStateManager.logout() }
}

// --- voice ---

// No recorder, no backend: the test sets `drafts` and the flow behaves as if the
// user had dictated them.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [VoiceModule::class])
object TestVoiceModule {

    @Provides
    @Singleton
    fun provideVoiceRepository(): VoiceRepository = TestVoiceRepository()
}

@Singleton
class TestVoiceRepository @Inject constructor() : VoiceRepository {

    @Volatile
    var drafts: List<ItemDraft> = emptyList()

    @Volatile
    var failWith: AppError? = null

    override suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>> =
        failWith?.let { Result.Error(it) } ?: Result.Success(drafts)
}

// --- supabase ---

// The real client is never contacted, but MainActivity's graph still needs one to
// exist. Pointed at a loopback host so any stray call fails fast and locally
// rather than reaching the production project from a test run.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SupabaseModule::class])
object TestSupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = "http://127.0.0.1:1",
        supabaseKey = "test-anon-key",
    ) {}
}
