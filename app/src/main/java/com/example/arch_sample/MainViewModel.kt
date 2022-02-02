package com.example.arch_sample

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arch_sample.use_case.LoginUseCase
import com.example.arch_sample.use_case.UserData
import com.example.arch_sample.util.Failure
import com.example.arch_sample.util.Result
import com.example.arch_sample.util.Success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

@FlowPreview
@Suppress("ComplexRedundantLet")
class MainViewModel(
    private val appCoroutineScope: CoroutineScope,
    private val vmAppSubStateLiveData: LiveData<MapVmAppSubState?>,
    private val appActionHandler: (AppAction) -> Unit
) : ViewModel() {

    private var state: State = NotLoggedIn(null)
    val viewState = MutableLiveData<ViewState>()
    val toastEvent = MutableLiveData<String>()

    init {
        // will be bound to vm lifecycle
        vmAppSubStateLiveData.observeForever {
            getAppSubState().let {
                when(it) {
                    is Success -> AppStateChangedAction(it.data)
                    is Failure -> ErrorAction(it.exception)
                }
            }.let {
                handleAction(it)
            }
        }
    }

    fun onLogin() {
        handleAction(LoginClickedAction)
    }

    fun onLogout() {
        handleAction(LogoutClickedAction)
    }

    fun onTextChanged(text: String) {
        handleAction(TextChangedAction(text))
    }

    fun onError(exception: Exception) {
        handleAction(ErrorAction(exception))
    }

    private fun handleAction(action: Action) {
        viewModelScope.launch {
            state = state.let { oldState ->
                getAppSubState().let {
                    when (it) {
                        is Success -> {
                            val result = reducer(oldState, action).let { result ->
                                result.mapEffects { effects ->
                                    effects + updateViewStateEffect(result.newState)
                                }
                            }
                            handleEffects(it.data, result.effects)
                            result.newState
                        }
                        is Failure -> {
                            Error(it.exception)
                        }
                    }
                }
            }
        }
    }

    private fun reducer(
        state: State,
        action: Action
    ): ReducerResult<State, Effect> {
        return when (action) {
            is TextChangedAction -> {
                when (state) {
                    is NotLoggedIn -> state.copy(currentUsername = action.text).let {
                        ReducerResult(it, setOf())
                    }
                    is LoggedIn, is Error -> {
                        ReducerResult(state, setOf())
                    }
                }
            }
            LoginClickedAction -> {
                when (state) {
                    is NotLoggedIn -> ReducerResult(
                        state,
                        setOf(
                            if (!state.currentUsername.isNullOrBlank()) {
                                LogInEffect(state.currentUsername)
                            } else {
                                MakeToastEffect("Username can't be empty")
                            }
                        )
                    )
                    is LoggedIn, is Error -> {
                        Error(IllegalActionException(action, state)).let {
                            ReducerResult(it, setOf())
                        }
                    }
                }
            }
            is ErrorAction -> {
                when (state) {
                    is Error -> state
                    is LoggedIn, is NotLoggedIn -> Error(action.exception)
                }.let {
                    ReducerResult(it, setOf())
                }
            }
            is TriggerAppAction -> {
                ReducerResult(
                    state,
                    setOf(TriggerAppActionEffect(action.appAction))
                )
            }
            is AppStateChangedAction -> {
                val userState = action.appSubState.userState
                when(state) {
                    is LoggedIn -> {
                        when(userState) {
                            is UserLoggedIn -> {
                                LoggedIn(userState.username)
                            }
                            UserNotLoggedIn -> {
                                NotLoggedIn(null)
                            }
                        }
                    }
                    is NotLoggedIn -> {
                        when(userState) {
                            is UserLoggedIn -> LoggedIn(userState.username)
                            UserNotLoggedIn -> state
                        }
                    }
                    is Error -> {
                        state
                    }
                }.let { newState ->
                    ReducerResult(newState, setOf())
                }
            }
            LogoutClickedAction -> {
                when(state) {
                    is LoggedIn -> ReducerResult(state, setOf(
                        TriggerAppActionEffect(LogOutAppAction)
                    ))
                    is Error -> ReducerResult(state, setOf())
                    is NotLoggedIn -> Error(IllegalActionException(action, state)).let {
                        ReducerResult(it, setOf())
                    }
                }
            }
        }
    }

    private fun updateViewStateEffect(state: State): Set<Effect> {
        return setOf(
            UpdateViewStateEffect(
                when (state) {
                    is LoggedIn -> ViewState(
                        loginViewsVisible = false,
                        logoutViewsVisible = true,
                        textState = state.toString(),
                        username = state.username
                    )
                    is NotLoggedIn -> ViewState(
                        loginViewsVisible = true,
                        logoutViewsVisible = false,
                        textState = state.toString(),
                        username = state.currentUsername.orEmpty()
                    )
                    is Error -> ViewState(
                        loginViewsVisible = false,
                        logoutViewsVisible = false,
                        textState = state.toString(),
                        username = ""
                    )
                }
            )
        )
    }

    private fun handleEffects(appSubState: MapVmAppSubState, effects: Set<Effect>) {
        effects.map {
            Log.v("hypertrack-verbose", it.toString())
            mapEffect(appSubState, it)
        }.forEach { effectFlow ->
            appCoroutineScope.launch {
                effectFlow.collect { action ->
                    Log.v("hypertrack-verbose", action.toString())
                    action?.let { handleAction(action) }
                }
            }
        }
    }

    private fun mapEffect(appSubState: MapVmAppSubState, effect: Effect): Flow<Action?> {
        return when (effect) {
            is LogInEffect -> {
                appSubState.loginUseCase.flow(effect.username)
                    .flatMapConcat { result ->
                        when (result) {
                            is Success -> {
                                makeToast("Login success").map { Success(result.data) }
                            }
                            is Failure -> {
                                flowOf(result)
                            }
                        }
                    }
                    .map {
                        when (it) {
                            is Success -> TriggerAppAction(LoggedInAppAction(it.data))
                            is Failure -> ErrorAction(it.exception)
                        }
                    }

            }
            is UpdateViewStateEffect -> {
                {
                    viewState.postValue(effect.viewState)
                    null
                }.asFlow()
            }
            is MakeToastEffect -> {
                makeToast(effect.text).map { null }
            }
            is TriggerAppActionEffect -> {
                {
                    appActionHandler.invoke(effect.action)
                    null
                }.asFlow()
            }
        }
    }

    private fun getAppSubState(): Result<MapVmAppSubState> {
        return vmAppSubStateLiveData.value?.let { Success(it) } ?: Failure(Exception("Failed to get app state"))
    }

    private fun makeToast(text: String): Flow<Any> {
        return {
            toastEvent.postValue(text)
            true
        }.asFlow()
    }
}


sealed class Action
class ErrorAction(val exception: Exception) : Action()
class TextChangedAction(val text: String) : Action()
class AppStateChangedAction(val appSubState: MapVmAppSubState) : Action()
class TriggerAppAction(val appAction: AppAction) : Action()

object LoginClickedAction : Action()
object LogoutClickedAction : Action()

sealed class State
data class NotLoggedIn(val currentUsername: String?) : State()
data class LoggedIn(val username: String) : State()
data class Error(val exception: Exception) : State()

sealed class Effect
class LogInEffect(val username: String) : Effect()
class MakeToastEffect(val text: String) : Effect()
class TriggerAppActionEffect(val action: AppAction) : Effect()
class UpdateViewStateEffect(val viewState: ViewState) : Effect()

class ViewState(
    val loginViewsVisible: Boolean,
    val logoutViewsVisible: Boolean,
    val textState: String,
    val username: String,
)

// the part of app state that is needed for vm
// dependencies scopes are part of the state
class MapVmAppSubState(
    val userState: UserState,
    val loginUseCase: LoginUseCase
)

class IllegalActionException(action: Any, state: Any) :
    IllegalStateException("Illegal action $action for state $state")


