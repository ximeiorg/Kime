package com.kingzcheung.kime.service

import com.kingzcheung.kime.settings.SchemaInfo
import com.kingzcheung.kime.speech.RecognitionState

data class InputUIState(
    val candidates: Array<String> = emptyArray(),
    val candidateComments: Array<String> = emptyArray(),
    val inputText: String = "",
    val isComposing: Boolean = false,
    val isAsciiMode: Boolean = false,
    val schemaName: String = "",
    val currentSchemaId: String = "",
    val schemas: List<SchemaInfo> = emptyList(),
    val enterKeyText: String = "发送",
    val darkMode: Int = 0,
    val themeId: String = "ocean_blue",
    val showBottomButtons: Boolean = false,
    val keyboardHeightDp: Int = 290,
    val keyboardBottomPaddingDp: Int = 40,
    val showKeyboardResize: Boolean = false,
    val resizePreviewHeightDp: Int = 290,
    val resizePreviewBottomPaddingDp: Int = 40,
    val originalKeyboardHeightDp: Int = 290,
    val originalKeyboardBottomPaddingDp: Int = 40,
    val associationCandidates: Array<String> = emptyArray(),
    val associationEnabled: Boolean = false,
    val isVoiceMode: Boolean = false,
    val voiceButtonState: VoiceButtonState = VoiceButtonState(),
    val voicePluginName: String = "",
    val voiceRecognitionState: RecognitionState = RecognitionState.IDLE,
    val voiceRecognizedText: String = "",
    val voiceAmplitude: Float = 0f,
    val pendingEnglishText: String = "",
    val stretchFactor: Float = 1f,
    val isShowingRecentClipboard: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InputUIState

        if (!candidates.contentEquals(other.candidates)) return false
        if (!candidateComments.contentEquals(other.candidateComments)) return false
        if (inputText != other.inputText) return false
        if (isComposing != other.isComposing) return false
        if (isAsciiMode != other.isAsciiMode) return false
        if (schemaName != other.schemaName) return false
        if (currentSchemaId != other.currentSchemaId) return false
        if (schemas != other.schemas) return false
        if (enterKeyText != other.enterKeyText) return false
        if (darkMode != other.darkMode) return false
        if (themeId != other.themeId) return false
if (showBottomButtons != other.showBottomButtons) return false
    if (keyboardHeightDp != other.keyboardHeightDp) return false
    if (keyboardBottomPaddingDp != other.keyboardBottomPaddingDp) return false
    if (showKeyboardResize != other.showKeyboardResize) return false
    if (resizePreviewHeightDp != other.resizePreviewHeightDp) return false
    if (resizePreviewBottomPaddingDp != other.resizePreviewBottomPaddingDp) return false
    if (originalKeyboardHeightDp != other.originalKeyboardHeightDp) return false
    if (originalKeyboardBottomPaddingDp != other.originalKeyboardBottomPaddingDp) return false
        if (!associationCandidates.contentEquals(other.associationCandidates)) return false
        if (associationEnabled != other.associationEnabled) return false
        if (isVoiceMode != other.isVoiceMode) return false
        if (voiceButtonState != other.voiceButtonState) return false
        if (voicePluginName != other.voicePluginName) return false
        if (voiceRecognitionState != other.voiceRecognitionState) return false
        if (voiceRecognizedText != other.voiceRecognizedText) return false
        if (voiceAmplitude != other.voiceAmplitude) return false
        if (pendingEnglishText != other.pendingEnglishText) return false
        if (stretchFactor != other.stretchFactor) return false
        if (isShowingRecentClipboard != other.isShowingRecentClipboard) return false

        return true
    }

    override fun hashCode(): Int {
        var result = candidates.contentHashCode()
        result = 31 * result + candidateComments.contentHashCode()
        result = 31 * result + inputText.hashCode()
        result = 31 * result + isComposing.hashCode()
        result = 31 * result + isAsciiMode.hashCode()
        result = 31 * result + schemaName.hashCode()
        result = 31 * result + currentSchemaId.hashCode()
        result = 31 * result + schemas.hashCode()
        result = 31 * result + enterKeyText.hashCode()
        result = 31 * result + darkMode
        result = 31 * result + themeId.hashCode()
result = 31 * result + showBottomButtons.hashCode()
    result = 31 * result + keyboardHeightDp
    result = 31 * result + keyboardBottomPaddingDp
    result = 31 * result + showKeyboardResize.hashCode()
    result = 31 * result + resizePreviewHeightDp
    result = 31 * result + resizePreviewBottomPaddingDp
    result = 31 * result + originalKeyboardHeightDp
    result = 31 * result + originalKeyboardBottomPaddingDp
        result = 31 * result + associationCandidates.contentHashCode()
        result = 31 * result + associationEnabled.hashCode()
        result = 31 * result + isVoiceMode.hashCode()
        result = 31 * result + voiceButtonState.hashCode()
        result = 31 * result + voicePluginName.hashCode()
        result = 31 * result + voiceRecognitionState.hashCode()
        result = 31 * result + voiceRecognizedText.hashCode()
        result = 31 * result + voiceAmplitude.hashCode()
        result = 31 * result + pendingEnglishText.hashCode()
        result = 31 * result + stretchFactor.hashCode()
        result = 31 * result + isShowingRecentClipboard.hashCode()
        return result
    }
}