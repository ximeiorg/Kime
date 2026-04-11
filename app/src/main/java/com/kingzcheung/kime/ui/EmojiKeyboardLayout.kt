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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kingzcheung.kime.plugin.ExtensionManager
import com.kingzcheung.kime.plugin.api.EmojiItem
import com.kingzcheung.kime.clipboard.ClipboardManager
import com.kingzcheung.kime.settings.SettingsPreferences
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
                "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈",
                "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦",
                "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🧄", "🧅", "🥔", "🍠",
                "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞",
                "🧇", "🥓", "🥩", "🍗", "🍖", "🦴", "🌭", "🍔", "🍟", "🍕",
                "🫓", "🥪", "🌯", "🥗", "🌮", "🍙", "🍚", "🍲", "🥘", "🫕"
            )
        ),
        EmojiCategory(
            name = "活动",
            icon = "⚽",
            emojis = listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
                "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥏", "🪃", "🥅",
                "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼",
                "🪂", "⛸️", "🥌", "⛷️", "🏂", "🎿", "🪂", "🏋️‍♂️", "🤼", "🤸",
                "🤺", "🤾", "🥏", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣",
                "🧗", "🚵", "🚴", "🎖️", "🏆", "🥇", "🥈", "🥉", "🏅", "🎪"
            )
        ),
        EmojiCategory(
            name = "物品",
            icon = "💻",
            emojis = listOf(
                "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️",
                "🗜️", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥",
                "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️",
                "🎛️", "🧭", "⏱️", "⏲️", "⏰", "🕰️", "⏳", "⌛", "📡", "🔋",
                "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴",
                "💶", "💷", "💰", "💳", "💎", "⚖️", " toolbox", "🔧", "🔨", "⛏️"
            )
        ),
        EmojiCategory(
            name = "符号",
            icon = "❤️",
            emojis = listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "♻️", "☮️",
                "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐",
                "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐",
                "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳",
                "🈶", "🈚", "🈸", "🈺", "🈷️", "⭐", "🆚", "💮", "🉐", "㊙️"
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
                    val emojiPlugins = ExtensionManager.getEnabledEmojiPlugins(context)
                    Log.d("EmojiKeyboard", "Found ${emojiPlugins.size} emoji plugins")
                    
                    emojiPlugins.forEach { plugin ->
                        Log.d("EmojiKeyboard", "Loading emojis from: ${plugin.name}")
                        
                        try {
                            val emojiItems = plugin.getEmojis(category = null, searchText = null, topK = 100)
                            
                            if (emojiItems.isNotEmpty()) {
                                pluginCategories.add(
                                    EmojiCategory(
                                        name = plugin.name,
                                        icon = "🎭",
                                        emojis = emptyList(),
                                        isPlugin = true,
                                        pluginId = plugin.id,
                                        emojiItems = emojiItems
                                    )
                                )
                                Log.d("EmojiKeyboard", "Loaded ${emojiItems.size} emojis from ${plugin.name}")
                            }
                        } catch (e: Exception) {
                            Log.e("EmojiKeyboard", "Error loading emojis from ${plugin.name}", e)
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
    var containerWidth by remember { mutableStateOf(0) }
    
    Box(
        modifier = modifier
            .onSizeChanged { size -> containerWidth = size.width }
            .clip(RoundedCornerShape(4.dp))
            .background(Color.LightGray.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
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
            val textLength = emojiItem.displayText.length
            val fontSize = when {
                textLength <= 3 -> 10.sp
                textLength <= 4 -> 8.sp
                textLength <= 5 -> 6.sp
                else -> 5.sp
            }
            
            Text(
                text = emojiItem.displayText,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}