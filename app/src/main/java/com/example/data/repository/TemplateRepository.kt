package com.example.data.repository

import com.example.data.local.dao.TemplateDao
import com.example.data.local.entity.TemplateEntity
import com.example.domain.model.TemplateCategory
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val templateDao: TemplateDao) {

    val allTemplates: Flow<List<TemplateEntity>> = templateDao.getAllTemplates()

    fun getTemplatesByCategory(category: TemplateCategory): Flow<List<TemplateEntity>> {
        return templateDao.getTemplatesByCategory(category)
    }

    suspend fun getTemplateById(id: Long): TemplateEntity? {
        return templateDao.getTemplateById(id)
    }

    suspend fun insertTemplate(template: TemplateEntity): Long {
        return templateDao.insertTemplate(template)
    }

    suspend fun updateTemplate(template: TemplateEntity) {
        templateDao.updateTemplate(template)
    }

    suspend fun deleteTemplate(template: TemplateEntity) {
        templateDao.deleteTemplate(template)
    }

    suspend fun deleteTemplateById(id: Long) {
        templateDao.deleteTemplateById(id)
    }
}
