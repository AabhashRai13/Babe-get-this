package com.babegetthis.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.babegetthis.android.navigation.BgtNavGraph
import com.babegetthis.android.ui.theme.BabeGetThisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BabeGetThisTheme {
                BgtNavGraph()
            }
        }
    }
}
