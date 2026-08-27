package com.example.wifiheatmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifiheatmap.data.model.WifiSnapshot
import com.example.wifiheatmap.wifi.WifiRepository
import com.example.wifiheatmap.wifi.WifiScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WifiDebugUiState(
    val isLoading: Boolean = false,
    val snapshot: WifiSnapshot? = null,
    val errorMessage: String? = null,
)

class WifiDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WifiRepository(WifiScanner(application))
    private val mutableUiState = MutableStateFlow(WifiDebugUiState())
    val uiState: StateFlow<WifiDebugUiState> = mutableUiState.asStateFlow()

    fun refresh(requestActiveScan: Boolean) {
        if (mutableUiState.value.isLoading) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { repository.loadSnapshot(requestActiveScan) }
                .onSuccess { snapshot ->
                    mutableUiState.value = WifiDebugUiState(snapshot = snapshot)
                }
                .onFailure { throwable ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Wi-Fi 정보를 읽지 못했습니다.",
                    )
                }
        }
    }
}
