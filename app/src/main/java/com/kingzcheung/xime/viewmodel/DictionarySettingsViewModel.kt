package com.kingzcheung.xime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.settings.DictEntry
import com.kingzcheung.xime.settings.DictionaryHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DictionaryUiState(
    val currentSchema: String = "wubi86",
    val schemaName: String = "",
    val searchQuery: String = "",
    val allEntries: List<DictEntry> = emptyList(),
    val displayedEntries: List<DictEntry> = emptyList(),
    val isLoading: Boolean = true
)

class DictionarySettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    
    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()
    
    init {
        loadDictionary()
    }
    
    private fun loadDictionary() {
        val currentSchema = SettingsPreferences.getCurrentSchema(context)
        val schemaMeta = SchemaManager.discoverSchemas(context).find { it.schemaId == currentSchema }
        
        _uiState.update { it.copy(
            currentSchema = currentSchema,
            schemaName = schemaMeta?.name ?: currentSchema
        )}
        
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                DictionaryHelper.loadDictionary(context, currentSchema)
            }
            
            _uiState.update { it.copy(
                allEntries = entries,
                displayedEntries = entries.take(50),
                isLoading = false
            )}
        }
    }
    
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        val allEntries = _uiState.value.allEntries
        val displayed = if (query.isEmpty()) {
            allEntries.take(50)
        } else {
            DictionaryHelper.searchDictionary(allEntries, query)
        }
        
        _uiState.update { it.copy(displayedEntries = displayed) }
    }
    
    fun clearSearch() {
        setSearchQuery("")
    }
}