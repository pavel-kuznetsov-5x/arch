package com.example.arch_sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.arch_sample.use_case.Api
import com.example.arch_sample.use_case.LoginUseCase
import com.example.arch_sample.use_case.SaveTokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

object Injector {

    private val appCoroutineScope = CoroutineScope(Dispatchers.Default)
    private var appState: AppState = Initial(
        AppScope(
            LoginUseCase(
                Api(),
                SaveTokenUseCase()
            )
        ), UserNotLoggedIn
    )

    val viewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when (modelClass) {
                MainViewModel::class.java -> MainViewModel(
                    appCoroutineScope,
                    appState.let {
                        when (it) {
                            is Initial -> MapVmAppSubState(
                                it.userState,
                                it.appScope.loginUseCase
                            )
                            else -> null
                        }
                    }
                ) as T
                else -> throw IllegalArgumentException("Can't instantiate class $modelClass")
            }
        }
    }

}
