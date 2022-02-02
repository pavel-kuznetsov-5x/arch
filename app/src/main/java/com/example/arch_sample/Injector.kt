package com.example.arch_sample

import androidx.lifecycle.*
import com.example.arch_sample.use_case.Api
import com.example.arch_sample.use_case.LoginUseCase
import com.example.arch_sample.use_case.SaveUserDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@FlowPreview
object Injector {

    private val appCoroutineScope = CoroutineScope(Dispatchers.Default)
    private var appState: AppState = Initial(
        AppScope(
            LoginUseCase(
                appCoroutineScope,
                Api(),
                SaveUserDataUseCase()
            )
        ), UserNotLoggedIn
    )
    private val appStateLiveData = MutableLiveData<AppState>(appState)

    val viewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when (modelClass) {
                MainViewModel::class.java -> MainViewModel(
                    appCoroutineScope,
                    appStateLiveData.map {
                        when (it) {
                            is Initial -> MapVmAppSubState(
                                it.userState,
                                it.appScope.loginUseCase
                            )
                            else -> null
                        }
                    }
                ) {
                    handleAction(it)
                }as T
                else -> throw IllegalArgumentException("Can't instantiate class $modelClass")
            }
        }
    }

    private fun handleAction(action: AppAction) {
        appCoroutineScope.launch {
            appState = appState.let { oldState ->
                val result = reducer(oldState, action).let { result ->
                    result.mapEffects { effects ->
                        effects + setOf()
                    }
                }
                handleEffects(result.effects)
                result.newState
            }.also { appStateLiveData.postValue(it) }
        }
    }



    private fun reducer(state: AppState, action: AppAction): ReducerResult<AppState, AppEffect> {
        return when(action) {
            is LoggedInAppAction -> {
                when(state) {
                    is Initial -> {
                        val newUserState = when(state.userState) {
                            is UserLoggedIn, UserNotLoggedIn -> {
                                UserLoggedIn(
                                    createUserScope(),
                                    action.userData.username,
                                    action.userData.token
                                )
                            }
                        }
                        ReducerResult(state.copy(userState = newUserState), setOf())
                    }
                }
            }
            LogOutAppAction -> {
                when(state) {
                    is Initial -> {
                        ReducerResult(state.copy(userState = UserNotLoggedIn), setOf())
                    }
                }
            }
        }
    }

    private fun handleEffects(effects: Set<AppEffect>) {
        effects.map {
            mapEffect(it)
        }.forEach { effectFlow ->
            appCoroutineScope.launch {
                effectFlow.collect { action ->
                    action?.let { handleAction(action) }
                }
            }
        }
    }

    private fun mapEffect(effect: AppEffect): Flow<AppAction?> {
        return when (effect) {
            else -> flow {}
        }
    }

    private fun createUserScope(): UserScope {
        return UserScope()
    }

}

open class ReducerResult<S, E>(val newState: S, val effects: Set<E>) {
    constructor(newState: S) : this(newState, setOf())

    fun mapEffects(mapper: (Set<E>) -> Set<E>): ReducerResult<S, E> {
        return ReducerResult(this.newState, mapper.invoke(this.effects))
    }
}

fun <R> (() -> R).asFlow(): Flow<R> {
    return flow {
        emit(this@asFlow.invoke())
    }
}
