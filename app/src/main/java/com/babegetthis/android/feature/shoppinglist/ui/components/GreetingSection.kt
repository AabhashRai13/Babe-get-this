package com.babegetthis.android.feature.shoppinglist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
// When signed in, the greeting is personalised with the user's first name.
@Composable
internal fun GreetingSection(
    listCount: Int,
    itemsToGet: Int,
    userName: String? = null,
) {
    // Personalise with the first name only ("Aabhash Rai" → "Aabhash").
    // Blank/null name (logged out) falls back to the plain greeting.
    val firstName = userName?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore(' ')
    val greeting = if (firstName != null) {
        "${getGreeting()}, $firstName"
    } else {
        getGreeting()
    }

    val summary = when {
        itemsToGet == 0 -> "$listCount lists — nothing to pick up yet"
        listCount == 1 -> "1 list · $itemsToGet items to get"
        else -> "$listCount lists · $itemsToGet items to get"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
    }
}
