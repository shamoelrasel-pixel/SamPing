package com.example.ui.screens.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.TemplateEntity
import com.example.domain.model.TemplateCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val templates: List<TemplateEntity> = emptyList(),
    val selectedCategory: TemplateCategory? = null,
    val searchQuery: String = "",
    val editingTemplate: TemplateEntity? = null,
    val showEditDialog: Boolean = false,
    val isLoading: Boolean = false
)

class TemplatesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val templateRepo = app.templateRepository

    private val _selectedCategory = MutableStateFlow<TemplateCategory?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _editingTemplate = MutableStateFlow<TemplateEntity?>(null)
    private val _showEditDialog = MutableStateFlow(false)

    val uiState: StateFlow<TemplatesUiState> = combine(
        templateRepo.allTemplates,
        _selectedCategory,
        _searchQuery,
        _editingTemplate,
        _showEditDialog
    ) { templates, category, query, editing, showDialog ->
        val filtered = templates.filter { item ->
            val matchesCategory = category == null || item.category == category
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.content.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }

        TemplatesUiState(
            templates = filtered,
            selectedCategory = category,
            searchQuery = query,
            editingTemplate = editing,
            showEditDialog = showDialog,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TemplatesUiState(isLoading = true)
    )

    fun selectCategory(category: TemplateCategory?) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openCreateDialog() {
        _editingTemplate.value = null
        _showEditDialog.value = true
    }

    fun openEditDialog(template: TemplateEntity) {
        _editingTemplate.value = template
        _showEditDialog.value = true
    }

    fun dismissEditDialog() {
        _editingTemplate.value = null
        _showEditDialog.value = false
    }

    fun saveTemplate(title: String, category: TemplateCategory, content: String) {
        viewModelScope.launch {
            val existing = _editingTemplate.value
            if (existing != null) {
                templateRepo.updateTemplate(
                    existing.copy(
                        title = title.trim(),
                        category = category,
                        content = content.trim()
                    )
                )
            } else {
                templateRepo.insertTemplate(
                    TemplateEntity(
                        title = title.trim(),
                        category = category,
                        content = content.trim()
                    )
                )
            }
            dismissEditDialog()
        }
    }

    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch {
            templateRepo.deleteTemplate(template)
        }
    }
}
