package com.kingzcheung.kime.ui

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kingzcheung.kime.settings.DictionaryHelper
import com.kingzcheung.kime.settings.DictEntry
import com.kingzcheung.kime.settings.SchemaConfigHelper
import com.kingzcheung.kime.settings.SettingsPreferences

object SettingsRoutes {
    const val Main = "main"
    const val Schema = "schema"
    const val Theme = "theme"
    const val KeyEffect = "key_effect"
    const val Dictionary = "dictionary"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.Main
    ) {
        composable(SettingsRoutes.Main) {
            SettingsMainContent(
                onNavigateToSchema = { navController.navigate(SettingsRoutes.Schema) },
                onNavigateToTheme = { navController.navigate(SettingsRoutes.Theme) },
                onNavigateToKeyEffect = { navController.navigate(SettingsRoutes.KeyEffect) },
                onNavigateToDictionary = { navController.navigate(SettingsRoutes.Dictionary) }
            )
        }
        composable(SettingsRoutes.Schema) {
            SchemaSettingsContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Theme) {
            ThemeSettingsContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.KeyEffect) {
            KeyEffectSettingsContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Dictionary) {
            DictionarySettingsContent(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainContent(
    onNavigateToSchema: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToKeyEffect: () -> Unit,
    onNavigateToDictionary: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F1F3))
    ) {
        TopAppBar(
            title = { Text("Kime 设置") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "输入法设置", content = {
                    SettingsItem(
                        icon = Icons.Outlined.Keyboard,
                        title = "启用输入法",
                        subtitle = "在系统设置中启用 Kime 输入法",
                        onClick = {
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Language,
                        title = "选择输入法",
                        subtitle = "将 Kime 设为当前输入法",
                        onClick = {
                            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) 
                                as InputMethodManager
                            imm.showInputMethodPicker()
                        }
                    )
                })
            }
            
            item {
                SettingsSection(title = "功能设置", content = {
                    SettingsItem(
                        icon = Icons.Outlined.KeyboardAlt,
                        title = "输入方案",
                        subtitle = "管理输入方案",
                        onClick = onNavigateToSchema,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = "主题与定制",
                        subtitle = "自定义外观和样式",
                        onClick = onNavigateToTheme,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Vibration,
                        title = "按键效果",
                        subtitle = "按键音效和振动反馈",
                        onClick = onNavigateToKeyEffect,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Mic,
                        title = "语言转文字",
                        subtitle = "语音输入设置",
                        onClick = { },
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
SettingsItem(
                        icon = Icons.Outlined.Book,
                        title = "词库管理",
                        subtitle = "管理用户词库",
                        onClick = onNavigateToDictionary,
                        showArrow = true
                    )
                })
            }
            
            item {
                SettingsSection(title = "关于", content = {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Kime 输入法",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "版本 1.0.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "基于 Rime 引擎的五笔输入法",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemaSettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val schemas = remember { SchemaConfigHelper.loadSchemas(context) }
    var currentSchema by remember { mutableStateOf(SettingsPreferences.getCurrentSchema(context)) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F1F3))
    ) {
        TopAppBar(
            title = { Text("输入方案") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "方案列表", content = {
                    schemas.forEachIndexed { index, schema ->
                        SchemaItem(
                            schema = schema,
                            isSelected = schema.schemaId == currentSchema,
                            onClick = {
                                if (currentSchema != schema.schemaId) {
                                    android.util.Log.d("Settings", "Selecting schema: ${schema.schemaId}")
                                    currentSchema = schema.schemaId
                                    SettingsPreferences.setCurrentSchema(context, schema.schemaId)
                                    android.util.Log.d("Settings", "Saved schema: ${SettingsPreferences.getCurrentSchema(context)}")
                                    Toast.makeText(context, "已切换到${schema.name}，请在输入法中点击\"重载配置\"生效", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        if (index < schemas.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                })
            }
            
            item {
                Text(
                    text = "提示: 切换方案后，请在输入法界面菜单中点击\"重载配置\"生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(SettingsPreferences.getDarkMode(context)) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F1F3))
    ) {
        TopAppBar(
            title = { Text("主题与定制") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "选择主题",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeCard(
                        title = "浅色",
                        isSelected = currentTheme == 0,
                        isDark = false,
                        onClick = {
                            currentTheme = 0
                            SettingsPreferences.setDarkMode(context, 0)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeCard(
                        title = "深色",
                        isSelected = currentTheme == 1,
                        isDark = true,
                        onClick = {
                            currentTheme = 1
                            SettingsPreferences.setDarkMode(context, 1)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            item {
                Text(
                    text = "提示: 切换主题后，请重启输入法生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyEffectSettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var soundEnabled by remember { mutableStateOf(SettingsPreferences.isSoundEnabled(context)) }
    var soundVolume by remember { mutableStateOf(SettingsPreferences.getSoundVolume(context)) }
    var vibrationEnabled by remember { mutableStateOf(SettingsPreferences.isVibrationEnabled(context)) }
    var vibrationIntensity by remember { mutableStateOf(SettingsPreferences.getVibrationIntensity(context)) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F1F3))
    ) {
        TopAppBar(
            title = { Text("按键效果") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "按键音效", content = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用按键音",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "按键时播放音效",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { 
                                soundEnabled = it
                                SettingsPreferences.setSoundEnabled(context, it)
                            }
                        )
                    }
                    
                    if (soundEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "音量大小",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$soundVolume%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = soundVolume.toFloat(),
                                onValueChange = { 
                                    soundVolume = it.toInt()
                                    SettingsPreferences.setSoundVolume(context, soundVolume)
                                },
                                valueRange = 0f..100f,
                                steps = 10
                            )
                        }
                    }
                })
            }
            
            item {
                SettingsSection(title = "振动反馈", content = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用振动",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "按键时振动反馈",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { 
                                vibrationEnabled = it
                                SettingsPreferences.setVibrationEnabled(context, it)
                            }
                        )
                    }
                    
                    if (vibrationEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "振动强度",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$vibrationIntensity%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = vibrationIntensity.toFloat(),
                                onValueChange = { 
                                    vibrationIntensity = it.toInt()
                                    SettingsPreferences.setVibrationIntensity(context, vibrationIntensity)
                                },
                                valueRange = 0f..100f,
                                steps = 10
                            )
                        }
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentSchema = SettingsPreferences.getCurrentSchema(context)
    val schemaInfo = SchemaConfigHelper.loadSchemas(context).find { it.schemaId == currentSchema }
    val dictFile = DictionaryHelper.getDictFileForSchema(currentSchema)
    
    var searchQuery by remember { mutableStateOf("") }
    var allEntries by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var displayedEntries by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(currentSchema) {
        isLoading = true
        allEntries = DictionaryHelper.loadDictionary(context, currentSchema)
        displayedEntries = allEntries.take(100)
        isLoading = false
    }
    
    LaunchedEffect(searchQuery) {
        displayedEntries = if (searchQuery.isEmpty()) {
            allEntries.take(100)
        } else {
            DictionaryHelper.searchDictionary(allEntries, searchQuery)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F1F3))
    ) {
        TopAppBar(
            title = { Text("词库管理") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SettingsSection(title = "当前词库", content = {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "输入方案",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = schemaInfo?.name ?: currentSchema,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "词库文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dictFile ?: "未知",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "词条总数: ${allEntries.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            })
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索词条或编码") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                SettingsSection(title = "词条列表 (${displayedEntries.size})", content = {
                    if (displayedEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isEmpty()) "暂无词条" else "未找到匹配词条",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        displayedEntries.forEachIndexed { index, entry ->
                            DictEntryItem(entry = entry)
                            if (index < displayedEntries.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun DictEntryItem(entry: DictEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.word,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = entry.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}