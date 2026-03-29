package com.kingzcheung.kime.ui

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.kingzcheung.kime.ui.theme.KeyboardThemes

object SettingsRoutes {
    const val Main = "main"
    const val Schema = "schema"
    const val Theme = "theme"
    const val KeyEffect = "key_effect"
    const val Dictionary = "dictionary"
    const val About = "about"
    const val Privacy = "privacy"
    const val Licenses = "licenses"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(initialRoute: String? = null) {
    val navController = rememberNavController()
    val startDestination = if (initialRoute == "manage_dict") SettingsRoutes.Dictionary else SettingsRoutes.Main
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(SettingsRoutes.Main) {
            SettingsMainContent(
                onNavigateToSchema = { navController.navigate(SettingsRoutes.Schema) },
                onNavigateToTheme = { navController.navigate(SettingsRoutes.Theme) },
                onNavigateToKeyEffect = { navController.navigate(SettingsRoutes.KeyEffect) },
                onNavigateToDictionary = { navController.navigate(SettingsRoutes.Dictionary) },
                onNavigateToAbout = { navController.navigate(SettingsRoutes.About) }
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
        composable(SettingsRoutes.About) {
            AboutContent(
                onBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(SettingsRoutes.Privacy) },
                onNavigateToLicenses = { navController.navigate(SettingsRoutes.Licenses) }
            )
        }
        composable(SettingsRoutes.Privacy) {
            PrivacyPolicyContent(
                onBack = { navController.popBackStack() }
            )
        }
        composable(SettingsRoutes.Licenses) {
            LicensesContent(
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
    onNavigateToDictionary: () -> Unit,
    onNavigateToAbout: () -> Unit
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
                    var showBottomButtons by remember { mutableStateOf(SettingsPreferences.showBottomButtons(context)) }
                    
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
                    SettingsToggleItem(
                        icon = Icons.Outlined.Visibility,
                        title = "显示底部按钮",
                        subtitle = "显示收回键盘和切换输入法按钮（部分系统自带）",
                        checked = showBottomButtons,
                        onCheckedChange = { newValue ->
                            showBottomButtons = newValue
                            SettingsPreferences.setShowBottomButtons(context, newValue)
                        }
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
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "关于 Kime",
                        subtitle = "版本 ${com.kingzcheung.kime.BuildConfig.VERSION_NAME}",
                        onClick = onNavigateToAbout,
                        showArrow = true
                    )
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
    var currentColorTheme by remember { mutableStateOf(SettingsPreferences.getKeyboardTheme(context)) }
    val colorThemes = KeyboardThemes.themes
    
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
                    text = "显示模式",
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "配色方案",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                Text(
                    text = "选择特殊按键（Shift、中英切换、确定等）的配色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                val rows = colorThemes.chunked(4)
                rows.forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            KeyboardThemeCard(
                                theme = theme,
                                isSelected = currentColorTheme == theme.id,
                                isDark = currentTheme == 1,
                                onClick = {
                                    currentColorTheme = theme.id
                                    SettingsPreferences.setKeyboardTheme(context, theme.id)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(4 - rowThemes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
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
    
    var searchQuery by remember { mutableStateOf("") }
    var allEntries by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var displayedEntries by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(currentSchema) {
        isLoading = true
        allEntries = DictionaryHelper.loadDictionary(context, currentSchema)
        displayedEntries = allEntries.take(50)
        isLoading = false
    }
    
    LaunchedEffect(searchQuery) {
        displayedEntries = if (searchQuery.isEmpty()) {
            allEntries.take(50)
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
            title = { 
                Column {
                    Text("词库管理")
                    Text(
                        text = schemaInfo?.name ?: currentSchema,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
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
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "搜索词条或编码",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = "共 ${allEntries.size} 条词条${if (searchQuery.isNotEmpty()) "，搜索结果 ${displayedEntries.size} 条" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                if (displayedEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "暂无词条" else "未找到匹配词条",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    SettingsSection(title = "词条列表", content = {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(displayedEntries.take(50)) { entry ->
                                DictEntryItem(entry = entry)
                            }
                        }
                    })
                }
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