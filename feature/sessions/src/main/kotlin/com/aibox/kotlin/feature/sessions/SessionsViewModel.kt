package com.aibox.kotlin.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.kotlin.core.model.SessionMeta
import com.aibox.kotlin.core.storage.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionsUiState(
    val sessions: List<SessionMeta> = emptyList(),
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val state: StateFlow<SessionsUiState> = sessionRepository.observeSessions()
        .map { SessionsUiState(sessions = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionsUiState())

    fun delete(sessionId: String) {
        viewModelScope.launch { sessionRepository.deleteSession(sessionId) }
    }

    fun toggleStar(sessionId: String, current: Boolean) {
        viewModelScope.launch { sessionRepository.setStarred(sessionId, !current) }
    }

    fun rename(sessionId: String, name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch { sessionRepository.renameSession(sessionId, name.trim()) }
        }
    }
}
