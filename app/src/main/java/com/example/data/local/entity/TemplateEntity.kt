package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.TemplateCategory

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: TemplateCategory = TemplateCategory.CUSTOM,
    val content: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
