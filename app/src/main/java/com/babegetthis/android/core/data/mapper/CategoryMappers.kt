package com.babegetthis.android.core.data.mapper

import com.babegetthis.android.core.data.local.model.CategoryEntity
import com.babegetthis.android.core.model.Category

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        isDefault = isDefault,
    )

fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        isDefault = isDefault,
    )
