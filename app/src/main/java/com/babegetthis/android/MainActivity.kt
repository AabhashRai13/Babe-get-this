package com.babegetthis.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.navigation.BgtNavGraph
import com.babegetthis.android.ui.theme.BabeGetThisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hilt injects the singleton AuthStateManager here.
    // Like using GetIt.instance<AuthStateManager>() in Flutter.
    @Inject lateinit var authStateManager: AuthStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if user has a saved token — this determines the start screen
        authStateManager.initialize()

        setContent {
            BabeGetThisTheme {
                BgtNavGraph(authStateManager = authStateManager)
            }
        }
    }
}
