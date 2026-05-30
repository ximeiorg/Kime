package com.kingzcheung.xime.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.LibraryBooks
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.CloudSync
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material.icons.twotone.KeyboardAlt
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Straighten
import androidx.compose.material.icons.twotone.ToggleOn
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kingzcheung.xime.ui.SettingsItem
import com.kingzcheung.xime.ui.SettingsSection
import com.kingzcheung.xime.ui.SettingsToggleItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainContent(
    onNavigateToSchema: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToKeyEffect: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSmartPrediction: () -> Unit,
    onNavigateToSpeechToText: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToWebDav: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumTopAppBar(
                title = { Text("曦码设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = Color.Unspecified
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "输入法", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Keyboard,
                        title = "启用输入法",
                        subtitle = "在系统设置中启用曦码输入法",
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
                        icon = Icons.TwoTone.ToggleOn,
                        title = "选择输入法",
                        subtitle = "将曦码设为当前输入法",
                        onClick = {
                            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) 
                                as InputMethodManager
                            imm.showInputMethodPicker()
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var testText by remember { mutableStateOf("") }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { 
                                Text(
                                    "点击此处开始输入测试...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                ) 
                            },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        if (testText.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { testText = "" }) {
                                    Text(
                                        "清除",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                })
            }

            item {
                SettingsSection(title = "方案与词库", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.KeyboardAlt,
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
                        icon = Icons.AutoMirrored.TwoTone.LibraryBooks,
                        title = "词库管理",
                        subtitle = "管理用户词库",
                        onClick = onNavigateToDictionary,
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "外观与交互", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Palette,
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
                        icon = Icons.TwoTone.Vibration,
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
                    var showBottomButtons by remember { mutableStateOf(SettingsPreferences.showBottomButtons(context)) }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.Straighten,
                        title = "显示底部按钮",
                        subtitle = "显示收回键盘和切换输入法按钮（部分系统自带）",
                        checked = showBottomButtons,
                        onCheckedChange = { newValue ->
                            showBottomButtons = newValue
                            SettingsPreferences.setShowBottomButtons(context, newValue)
                        }
                    )
                })
            }

            item {
                SettingsSection(title = "智能与扩展", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.AutoAwesome,
                        title = "智能联想",
                        subtitle = "基于 AI 模型的智能联想词预测",
                        onClick = onNavigateToSmartPrediction,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var sttEnabled by remember { mutableStateOf(SettingsPreferences.isSttEnabled(context)) }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.GraphicEq,
                        title = "语音转文本",
                        subtitle = "在线 ASR 服务和本地模型管理",
                        checked = sttEnabled,
                        showArrow = true,
                        onClick = {
                            if (sttEnabled) onNavigateToSpeechToText()
                        },
                        onCheckedChange = { enabled ->
                            sttEnabled = enabled
                            SettingsPreferences.setSttEnabled(context, enabled)
                            if (enabled && SettingsPreferences.isSttUseLocal(context)) {
                                MainScope().launch {
                                    try {
                                        val engine = com.kingzcheung.xime.speech.sherpa.SherpaAsrEngine(context)
                                        withContext(Dispatchers.IO) {
                                            engine.initialize()
                                        }
                                        engine.release()
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Extension,
                        title = "插件管理",
                        subtitle = "管理已安装的插件",
                        onClick = onNavigateToPlugins,
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "同步与备份", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.CloudSync,
                        title = "WebDAV 同步",
                        subtitle = "通过 WebDAV 备份和恢复输入方案与配置",
                        onClick = onNavigateToWebDav,
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = "关于", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Description,
                        title = "使用文档",
                        subtitle = "ime.ximei.me",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ime.ximei.me"))
                            context.startActivity(intent)
                        },
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Info,
                        title = "关于曦码",
                        subtitle = "版本信息、开发者、联系方式",
                        onClick = onNavigateToAbout,
                        showArrow = true
                    )
                })
            }
        }
    }
}
