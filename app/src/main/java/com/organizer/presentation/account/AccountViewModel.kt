package com.organizer.presentation.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.auth.AuthRepository
import com.organizer.auth.UserState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val userState: StateFlow<UserState> = authRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentState())

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            authRepository.signInWithGoogle(activity)
        }
    }

    fun sendSignInLink(email: String) {
        viewModelScope.launch {
            authRepository.sendSignInLink(email)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}