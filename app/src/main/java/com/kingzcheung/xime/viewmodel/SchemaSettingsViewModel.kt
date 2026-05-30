package com.kingzcheung.xime.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SchemaMeta
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SchemaUiState(
    val allSchemas: List<SchemaMeta> = emptyList(),
    val enabledSchemas: List<String> = emptyList(),
    val currentSchema: String = "wubi86",
    val isDeploying: Boolean = false,
    val isDownloading: Boolean = false,
    val toastMessage: String? = null
)

class SchemaSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(SchemaUiState())
    val uiState: StateFlow<SchemaUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val allSchemas = SchemaManager.discoverSchemas(context)
        val enabledSchemas = SchemaManager.getEnabledSchemas(context)
        val currentSchema = SettingsPreferences.getCurrentSchema(context)

        val sorted = allSchemas.sortedByDescending { it.schemaId in enabledSchemas }

        _uiState.update {
            it.copy(
                allSchemas = sorted,
                enabledSchemas = enabledSchemas,
                currentSchema = currentSchema
            )
        }
    }

    fun toggleSchema(schema: SchemaMeta) {
        val enabled = _uiState.value.enabledSchemas.toMutableList()
        if (schema.schemaId in enabled) {
            enabled.remove(schema.schemaId)
        } else {
            enabled.add(schema.schemaId)
        }
        SchemaManager.setEnabledSchemas(context, enabled)
        _uiState.update { it.copy(enabledSchemas = enabled) }
    }

    fun selectSchema(schema: SchemaMeta) {
        if (_uiState.value.currentSchema == schema.schemaId) return
        SettingsPreferences.setCurrentSchema(context, schema.schemaId)
        _uiState.update { it.copy(currentSchema = schema.schemaId) }
        if (RimeEngine.isInitialized()) {
            val available = RimeEngine.getInstance().getAvailableSchemas()
            if (schema.schemaId in available) {
                RimeEngine.getInstance().switchSchema(schema.schemaId)
                showToast("已切换到${schema.name}")
            } else {
                showToast("请点击「部署」按钮")
            }
        }
    }

    fun importSchemaFile(uri: Uri) {
        viewModelScope.launch {
            val success = SchemaManager.importSchemaFile(context, uri)
            refresh()
            if (success) {
                val importedId = detectImportedSchemaId(uri)
                if (importedId != null && SchemaManager.schemaNeedsDict(context, importedId) && !SchemaManager.hasDictFile(context, importedId)) {
                    showToast("${importedId}.schema.yaml 已导入，但缺少对应的 .dict.yaml 词典文件，编译会失败")
                } else {
                    showToast("导入成功")
                }
            } else {
                showToast("导入失败")
            }
        }
    }

    private fun detectImportedSchemaId(uri: Uri): String? {
        val name = uri.lastPathSegment ?: return null
        return when {
            name.endsWith(".schema.yaml") -> name.removeSuffix(".schema.yaml")
            name.endsWith(".dict.yaml") -> name.removeSuffix(".dict.yaml")
            else -> null
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true) }
            val success = withContext(Dispatchers.IO) {
                SchemaManager.importFromUrl(getApplication(), url)
            }
            _uiState.update { it.copy(isDownloading = false) }
            refresh()
            showToast(if (success) "导入成功" else "下载或解压失败，请检查链接")
        }
    }

    fun deleteSchema(schema: SchemaMeta) {
        SchemaManager.deleteSchemaFiles(context, schema.schemaId)
        if (_uiState.value.currentSchema == schema.schemaId) {
            val remaining = _uiState.value.allSchemas.firstOrNull { it.schemaId != schema.schemaId }
            if (remaining != null) {
                selectSchema(remaining)
            }
        }
        refresh()
        showToast("${schema.name} 已删除")
    }

    fun deploySchema() {
        if (_uiState.value.isDeploying) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeploying = true) }
            val success = withContext(Dispatchers.IO) {
                RimeEngine.getInstance().deploy()
            }
            _uiState.update { it.copy(isDeploying = false) }
            showToast(if (success) "部署完成" else "部署失败")
            refresh()
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }
}
