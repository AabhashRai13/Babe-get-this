package com.babegetthis.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.babegetthis.android.feature.home.presentation.HomeScreen
import com.babegetthis.android.ui.theme.BabeGetThisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BabeGetThisTheme {
                BabeGetThisApp()
            }
        }
    }
}

@Composable
private fun BabeGetThisApp() {
    // Later this becomes: NavGraph()
    HomeScreen()
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    BabeGetThisTheme {
        BabeGetThisApp()
    }
}
