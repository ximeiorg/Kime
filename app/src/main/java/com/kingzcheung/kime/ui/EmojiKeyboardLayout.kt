package com.kingzcheung.kime.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kingzcheung.kime.plugin.ExtensionManager
import com.kingzcheung.kime.plugin.api.ExtensionInput
import com.kingzcheung.kime.plugin.api.ExtensionResult
import com.kingzcheung.kime.plugin.api.ExtensionType
import com.kingzcheung.kime.plugin.api.EmojiItem
import com.kingzcheung.kime.clipboard.ClipboardManager
import kotlinx.coroutines.launch

data class EmojiCategory(
    val name: String,
    val icon: String,
    val emojis: List<String>,
    val isPlugin: Boolean = false,
    val pluginId: String? = null,
    val emojiItems: List<EmojiItem>? = null
)

object EmojiData {
    val categories = listOf(
        EmojiCategory(
            name = "笑脸",
            icon = "😊",
            emojis = listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
                "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
                "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫",
                "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
                "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢",
                "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸",
                "😎", "🤓", "🧐", "😕", "😟", "🙁", "☹️", "😮", "😯", "😲",
                "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱",
                "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
                "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻"
            )
        ),
        EmojiCategory(
            name = "手势",
            icon = "👋",
            emojis = listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞",
                "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍",
                "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂",
                "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅",
                "👄", "💪", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁"
            )
        ),
        EmojiCategory(
            name = "动物",
            icon = "🐶",
            emojis = listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
                "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔",
                "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺",
                "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟",
                "🦗", "🕷️", "🦂", "乌龟", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑",
                "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈"
            )
        ),
        EmojiCategory(
            name = "食物",
            icon = "🍎",
            emojis = listOf(
                "🍎", "梨", "🍊", "柠檬", "🍌", "西瓜", "葡萄", "草莓", "蓝莓", "🍈",
                "樱桃", "桃", "芒果", "菠萝", "椰子", "猕猴桃", "番茄", "茄子", " avocado", "西兰花",
                "白菜", "黄瓜", "辣椒", "青椒", "玉米", "胡萝卜", "大蒜", "洋葱", "土豆", "红薯",
                "羊角面包", "贝果", "面包", "法棍", "椒盐卷饼", "奶酪", "蛋", "煎蛋", "黄油", "煎饼",
                "华夫饼", "培根", "牛排", "鸡肉", "排骨", "骨头", "热狗", "汉堡", "薯条", "披萨",
                "饼", "三明治", "卷饼", "沙拉", "墨西哥卷", "饭团", "沙拉", "火锅", "煲", "奶酪锅"
            )
        ),
        EmojiCategory(
            name = "活动",
            icon = "⚽",
            emojis = listOf(
                "⚽", "🏀", "🏈", "棒球", "垒球", "网球", "排球", "橄榄球", "飞盘", "台球",
                "悠悠球", "乒乓球", "羽毛球", "冰球", "曲棍球", "长曲棍球", "板球", "飞盘", "回旋镖", "球门",
                "高尔夫", "风筝", "射箭", "钓鱼", "潜水", "拳击", "武术", "背心", "滑板", "轮滑",
                "雪橇", "滑冰", "冰壶", "滑雪", "滑雪者", "滑雪板", "降落伞", "举重", "摔跤", "体操",
                "击剑", "篮球", "手球", "高尔夫", "骑马", "瑜伽", "冲浪", "游泳", "水球", "划船",
                "攀岩", "骑行", "山地车", "勋章", "奖杯", "金牌", "银牌", "铜牌", "奖章", "马戏团"
            )
        ),
        EmojiCategory(
            name = "物品",
            icon = "💻",
            emojis = listOf(
                "手表", "手机", "📲", "💻", "键盘", "台式机", "打印机", "鼠标", "轨迹球", "操纵杆",
                "压缩机", "磁盘", "软盘", "光盘", "DVD", "录像带", "相机", "相机2", "摄像机", "电影摄影机",
                "放映机", "胶片", "电话", "座机", "传呼机", "传真机", "电视", "收音机", "麦克风", "调音台",
                "旋钮", "指南针", "秒表", "计时器", "闹钟", "座钟", "沙漏", "沙漏2", "卫星", "电池",
                "插头", "灯泡", "手电筒", "蜡烛", "油灯", "灭火器", "油桶", "零钱", "钞票", "日元",
                "欧元", "英镑", "钱袋", "信用卡", "宝石", "天平", "工具箱", "扳手", "锤子", "镐"
            )
        ),
        EmojiCategory(
            name = "符号",
            icon = "❤️",
            emojis = listOf(
                "❤️", "橙心", "黄心", "绿心", "蓝心", "紫心", "黑心", "白心", "棕心", "心碎",
                "感叹号", "双心", "两心", "心跳", "心动", "爱心", "丘比特", "礼物心", "循环", "和平",
                "十字架", "星月", "印度教", "佛教", "犹太教", "六芒星", "烛台", "道教", "东正教", "祈祷",
                "蛇夫座", "白羊", "金牛", "双子", "巨蟹", "狮子", "处女", "天秤", "天蝎", "射手",
                "摩羯", "水瓶", "双鱼", "ID", "原子", "可", "辐射", "生物危害", "关机", "震动",
                "有", "无", "申", "申2", "申3", "星号", "VS", "白花", "得", "秘"
            )
        )
    )
}

@Composable
fun EmojiKeyboardLayout(
    onEmojiSelect: (String) -> Unit,
    onImageEmojiSelect: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = remember { ClipboardManager.getInstance(context) }
    
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var allCategories by remember { mutableStateOf<List<EmojiCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val pluginCategories = mutableListOf<EmojiCategory>()
                
                if (ExtensionManager.isInitialized()) {
                    val emojiExtensions = ExtensionManager.getExtensionsByType(ExtensionType.EMOJI)
                    Log.d("EmojiKeyboard", "Found ${emojiExtensions.size} emoji extensions")
                    
                    emojiExtensions.forEach { extension ->
                        Log.d("EmojiKeyboard", "Loading emojis from: ${extension.name}")
                        
                        val result = extension.process(ExtensionInput(text = "", topK = 100))
                        
                        when (result) {
                            is ExtensionResult.Emojis -> {
                                if (result.items.isNotEmpty()) {
                                    pluginCategories.add(
                                        EmojiCategory(
                                            name = extension.name,
                                            icon = "🎭",
                                            emojis = emptyList(),
                                            isPlugin = true,
                                            pluginId = extension.id,
                                            emojiItems = result.items
                                        )
                                    )
                                    Log.d("EmojiKeyboard", "Loaded ${result.items.size} emojis from ${extension.name}")
                                }
                            }
                            is ExtensionResult.Error -> {
                                Log.e("EmojiKeyboard", "Error loading emojis from ${extension.name}: ${result.message}")
                            }
                            else -> {}
                        }
                    }
                }
                
                allCategories = pluginCategories + EmojiData.categories
                Log.d("EmojiKeyboard", "Total categories: ${allCategories.size}, plugin categories: ${pluginCategories.size}")
                
            } catch (e: Exception) {
                Log.e("EmojiKeyboard", "Failed to load plugin emojis", e)
                allCategories = EmojiData.categories
            } finally {
                isLoading = false
            }
        }
    }
    
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text("加载表情...", color = textColor, fontSize = 16.sp)
        }
        return
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(top = 8.dp, bottom = 36.dp, start = 4.dp, end = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp)
                .padding(bottom = 4.dp)
        ) {
            val currentCategory = allCategories.getOrElse(selectedCategoryIndex) { allCategories[0] }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (currentCategory.isPlugin && currentCategory.emojiItems != null) {
                    val hasImages = currentCategory.emojiItems.any { it.imageUrl != null }
                    val columns = if (hasImages) 6 else 8
                    
                    currentCategory.emojiItems.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowItems.forEach { item ->
                                PluginEmojiButton(
                                    emojiItem = item,
                                    onClick = {
                                        val imageUrl = item.imageUrl
                                        if (imageUrl != null && onImageEmojiSelect != null) {
                                            onImageEmojiSelect(imageUrl)
                                        } else if (imageUrl != null) {
                                            val success = clipboardManager.copyImageToSystemClipboard(
                                                imageUrl,
                                                item.displayText
                                            )
                                            if (success) {
                                                Toast.makeText(
                                                    context,
                                                    "已复制表情，可粘贴发送",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "复制失败",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            onEmojiSelect(item.insertText)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(if (hasImages) 60.dp else 40.dp)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                } else {
                    val emojis = currentCategory.emojis
                    val columns = 8
                    
                    emojis.chunked(columns).forEach { rowEmojis ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowEmojis.forEach { emoji ->
                                EmojiButton(
                                    emoji = emoji,
                                    onClick = { onEmojiSelect(emoji) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowEmojis.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                text = "返回",
                onClick = onBack,
                backgroundColor = backgroundColor,
                textColor = textColor,
                modifier = Modifier.width(48.dp),
                fontSize = 12.sp
            )
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                allCategories.forEachIndexed { index, category ->
                    EmojiCategoryTab(
                        icon = category.icon,
                        isSelected = index == selectedCategoryIndex,
                        onClick = { selectedCategoryIndex = index },
                        backgroundColor = backgroundColor,
                        textColor = textColor,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
            
            KeyButton(
                text = "删除",
                onClick = { onEmojiSelect("delete") },
                backgroundColor = backgroundColor,
                textColor = textColor,
                modifier = Modifier.width(48.dp),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun EmojiCategoryTab(
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) textColor.copy(alpha = 0.15f)
                else backgroundColor
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmojiButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PluginEmojiButton(
    emojiItem: EmojiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.LightGray.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (emojiItem.imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(emojiItem.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = emojiItem.displayText,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = emojiItem.displayText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}