package com.kingzcheung.kime.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.kime.plugin.ExtensionManager
import com.kingzcheung.kime.plugin.api.ExtensionType
import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.settings.SettingsPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsSettingsContent(
    onBack: () -> Unit,
    onNavigateToPluginSettings: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var extensions by remember { mutableStateOf<List<KimeExtension>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    fun refreshPlugins() {
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                val initSuccess = ExtensionManager.reload(context)
                extensions = ExtensionManager.getExtensions()
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        refreshPlugins()
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshPlugins() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (errorMsg != null) {
                item {
                    Text(
                        text = "加载失败: $errorMsg",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                item {
                    Text(
                        text = "已安装插件 (${extensions.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (extensions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "暂无已安装的插件",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "安装插件后将在此显示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    item {
                        SettingsSection(title = "插件列表", content = {
                            extensions.forEachIndexed { index, extension ->
                                ExtensionItem(
                                    extension = extension,
                                    onClick = {
                                        if (extension.hasSettings()) {
                                            onNavigateToPluginSettings(extension.id)
                                        }
                                    }
                                )
                                if (index < extensions.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        })
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "提示: 插件以独立 APK 形式安装，安装后点击右上角刷新按钮",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionItem(
    extension: KimeExtension,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(SettingsPreferences.isPluginEnabled(context, extension.id)) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (extension.hasSettings()) {
                    onClick()
                }
            }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (extension.hasSettings()) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "点击查看设置",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = getTypeName(extension.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "v${extension.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (extension.description.isNotEmpty()) {
                    Text(
                        text = extension.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    isEnabled = enabled
                    SettingsPreferences.setPluginEnabled(context, extension.id, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = {
                try {
                    val packageName = when (extension.id) {
                        "plugin_prediction_onnx" -> "com.kingzcheung.kime.plugin.prediction"
                        "kaomoji_plugin" -> "com.kingzcheung.kime.plugin.kaomoji"
                        "emoji_sticker_plugin" -> "com.kingzcheung.kime.plugin.emoji"
                        else -> extension.id
                    }
                    
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    context.startActivity(intent)
                    
                    android.widget.Toast.makeText(context, "请在应用信息页面卸载插件", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "无法打开应用详情: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("卸载插件")
        }
    }
}

private fun getTypeName(type: ExtensionType): String {
    return when (type) {
        ExtensionType.PREDICTION -> "联想词"
        ExtensionType.SPEECH -> "语音转文字"
        ExtensionType.EMOJI -> "表情推荐"
    }
}

private fun getTypeIcon(type: ExtensionType): ImageVector {
    return when (type) {
        ExtensionType.PREDICTION -> Icons.Default.AutoAwesome
        ExtensionType.SPEECH -> Icons.Default.Mic
        ExtensionType.EMOJI -> Icons.Default.Face
    }
}