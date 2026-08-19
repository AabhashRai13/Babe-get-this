package com.babegetthis.android.core.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    // true = pre-populated default, user can't delete these
    // false = user-created category
    val isDefault: Boolean,
)
