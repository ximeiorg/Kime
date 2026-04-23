package com.kingzcheung.kime.service

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import com.kingzcheung.kime.speech.RecognitionState

class VoiceKeyboardContainer(
    context: Context,
    private val uiStateProvider: () -> InputUIState,
    private val onUiStateChanged: (InputUIState) -> Unit,
    private val onPerformVibration: () -> Unit,
    private val onPerformUndo: () -> Unit,
    private val onPerformSearch: () -> Unit,
    private val onStopRecognition: () -> Unit,
    private val isRecording: () -> Boolean,
    private val setRecording: (Boolean) -> Unit
) : FrameLayout(context) {
    
    private var isTrackingVoiceButtons = false
    private var lastLeftActive = false
    private var lastRightActive = false
    
    fun updateHeight(heightDp: Int) {
        val heightPx = (heightDp * resources.displayMetrics.density).toInt()
        val params = layoutParams
        if (params != null && params.height != heightPx) {
            params.height = heightPx
            layoutParams = params
            requestLayout()
        }
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleActionDown(it)
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleActionUp()
                }
                
                MotionEvent.ACTION_MOVE -> {
                    handleActionMove(it)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
    
    private fun handleActionDown(ev: MotionEvent) {
        val isVoiceMode = uiStateProvider().isVoiceMode
        
        lastLeftActive = false
        lastRightActive = false
        
        if (isVoiceMode) {
            val yThreshold = height * 0.6f
            
            if (ev.y > yThreshold) {
                isTrackingVoiceButtons = true
                onUiStateChanged(uiStateProvider().copy(
                    voiceButtonState = VoiceButtonState(bottomActive = true)
                ))
            }
        }
    }
    
    private fun handleActionUp() {
        val state = uiStateProvider().voiceButtonState
        
        if (state.leftActive) {
            onPerformUndo()
        } else if (state.rightActive) {
            onPerformSearch()
        }
        
        if (isRecording() && isRecording()) {
            onStopRecognition()
            setRecording(false)
        }
        
        if (uiStateProvider().isVoiceMode) {
            onUiStateChanged(uiStateProvider().copy(
                isVoiceMode = false,
                voiceButtonState = VoiceButtonState(),
                voiceRecognitionState = RecognitionState.IDLE
            ))
        }
        
        isTrackingVoiceButtons = false
        lastLeftActive = false
        lastRightActive = false
    }
    
    private fun handleActionMove(ev: MotionEvent) {
        val isVoiceMode = uiStateProvider().isVoiceMode
        
        if (isVoiceMode && isTrackingVoiceButtons) {
            val yThreshold = height * 0.6f
            val leftButtonEnd = width * 0.25f
            val rightButtonStart = width * 0.75f
            
            if (ev.y > yThreshold) {
                when {
                    ev.x < leftButtonEnd -> {
                        if (!lastLeftActive) {
                            onPerformVibration()
                            lastLeftActive = true
                        }
                        onUiStateChanged(uiStateProvider().copy(
                            voiceButtonState = VoiceButtonState(leftActive = true)
                        ))
                    }
                    ev.x > rightButtonStart -> {
                        if (!lastRightActive) {
                            onPerformVibration()
                            lastRightActive = true
                        }
                        onUiStateChanged(uiStateProvider().copy(
                            voiceButtonState = VoiceButtonState(rightActive = true)
                        ))
                    }
                    else -> {
                        lastLeftActive = false
                        lastRightActive = false
                        onUiStateChanged(uiStateProvider().copy(
                            voiceButtonState = VoiceButtonState(bottomActive = true)
                        ))
                    }
                }
            } else if (ev.x < leftButtonEnd) {
                if (!lastLeftActive) {
                    onPerformVibration()
                    lastLeftActive = true
                }
                onUiStateChanged(uiStateProvider().copy(
                    voiceButtonState = VoiceButtonState(leftActive = true)
                ))
            } else if (ev.x > rightButtonStart) {
                if (!lastRightActive) {
                    onPerformVibration()
                    lastRightActive = true
                }
                onUiStateChanged(uiStateProvider().copy(
                    voiceButtonState = VoiceButtonState(rightActive = true)
                ))
            } else {
                lastLeftActive = false
                lastRightActive = false
                onUiStateChanged(uiStateProvider().copy(
                    voiceButtonState = VoiceButtonState()
                ))
            }
        }
    }
}