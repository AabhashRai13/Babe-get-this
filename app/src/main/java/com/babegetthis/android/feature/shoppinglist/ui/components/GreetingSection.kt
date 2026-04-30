package com.babegetthis.android.feature.shoppinglist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

// Time-aware greeting — small touch that makes the app feel personal.
private fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

// Greeting section — gives the home screen a warm focal point.
// Shows a time-aware greeting and a quick summary of active lists/items.
@Composable
internal fun GreetingSection(
    listCount: Int,
    itemsToGet: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp, start = 4.dp),
    ) {
        Text(
            text = getGreeting(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Summary line — shows remaining items to pick up
        val summary = when {
            itemsToGet == 0 -> "$listCount lists — nothing to pick up yet"
            listCount == 1 -> "1 list · $itemsToGet items to get"
            else -> "$listCount lists · $itemsToGet items to get"
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
