package com.kingzcheung.kime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.kime.association.ModelDownloadManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssociationSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var isModelDownloaded by remember { mutableStateOf(ModelDownloadManager.isModelDownloaded(context)) }
    var modelSize by remember { mutableStateOf(ModelDownloadManager.getModelSize(context)) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<ModelDownloadManager.DownloadProgress?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        isModelDownloaded = ModelDownloadManager.isModelDownloaded(context)
        modelSize = ModelDownloadManager.getModelSize(context)
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除模型") },
            text = { Text("确定要删除联想模型吗？删除后需要重新下载才能使用联想功能。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = ModelDownloadManager.deleteModel(context)
                        if (success) {
                            isModelDownloaded = false
                            modelSize = 0
                        } else {
                            errorMessage = "删除失败"
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能联想") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { errorMessage = null }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
Text(
                            text = "AI \u667a\u80fd\u8054\u60f3",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                            Text(
                                text = "基于上下文预测下一个词",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HorizontalDivider()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isModelDownloaded) {
                        ModelStatusCard(
                            icon = Icons.Default.CheckCircle,
                            iconColor = Color(0xFF4CAF50),
                            title = "模型已下载",
                            subtitle = "占用空间: ${ModelDownloadManager.formatSize(modelSize)}",
                            actionText = "删除模型",
                            onAction = { showDeleteDialog = true },
                            enabled = !isDownloading
                        )
                    } else if (isDownloading) {
                        ModelStatusCard(
                            icon = Icons.Default.Downloading,
                            iconColor = MaterialTheme.colorScheme.primary,
                            title = "正在下载...",
                            subtitle = downloadProgress?.let { progress ->
                                "${progress.fileName} (${progress.progress}%)"
                            } ?: "准备下载...",
                            actionText = "取消",
                            onAction = { 
                                // TODO: 实现取消下载
                            },
                            enabled = true,
                            showProgress = true,
                            progress = downloadProgress?.progress ?: 0
                        )
                    } else {
                        ModelStatusCard(
                            icon = Icons.Default.CloudDownload,
                            iconColor = MaterialTheme.colorScheme.primary,
                            title = "模型未下载",
                            subtitle = "约 17MB，从 ModelScope 下载",
                            actionText = "下载模型",
                            onAction = {
                                scope.launch {
                                    isDownloading = true
                                    errorMessage = null
                                    
                                    val success = ModelDownloadManager.downloadModel(context) { progress ->
                                        downloadProgress = progress
                                    }
                                    
                                    isDownloading = false
                                    downloadProgress = null
                                    
                                    if (success) {
                                        isModelDownloaded = true
                                        modelSize = ModelDownloadManager.getModelSize(context)
                                    } else {
                                        errorMessage = "下载失败，请检查网络连接后重试"
                                    }
                                }
                            },
                            enabled = true
                        )
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用说明",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    InstructionItem(
                        number = "1",
                        text = "下载模型文件（约 17MB）"
                    )
                    InstructionItem(
                        number = "2",
                        text = "在设置中开启"智能联想"开关"
                    )
                    InstructionItem(
                        number = "3",
                        text = "输入文字后，系统会自动预测下一个词"
                    )
                    InstructionItem(
                        number = "4",
                        text = "点击联想候选词即可输入"
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "模型信息",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    InfoItem(label = "来源", value = "ModelScope")
                    InfoItem(label = "模型名称", value = "predictive-text-small")
                    InfoItem(label = "推理框架", value = "ONNX Runtime")
                    InfoItem(label = "量化方式", value = "INT8 动态量化")
                    InfoItem(label = "词汇表大小", value = "8,192")
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
    enabled: Boolean,
    showProgress: Boolean = false,
    progress: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (showProgress) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = if (actionText == "删除模型") {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun InstructionItem(
    number: String,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}