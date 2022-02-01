package com.example.arch_sample

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arch_sample.use_case.LoginUseCase
import com.example.arch_sample.util.Failure
import com.example.arch_sample.util.Result
import com.example.arch_sample.util.Success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

@FlowPreview
@Suppress("ComplexRedundantLet")
class MainViewModel(
    private val appCoroutineScope: CoroutineScope,
    private val vmAppSubState: MapVmAppSubState?
) : ViewModel() {

    private var state: State = NotLoggedIn(null)
    val viewState = MutableLiveData<ViewState>()
    val toastEvent = MutableLiveData<String>()

    fun onLogin() {
        handleAction(LoginClickedAction)
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
                            val result = reducer(it.data, oldState, action).let { result ->
                                result.mapEffects { effects ->
                                    effects + updateViewStateEffect(result.newState)
                                }
                            }
                            handleEffects(result.effects)
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

    private fun updateViewStateEffect(state: State): Set<Effect<*>> {
        return setOf(UpdateViewStateEffect(flow {
            when(state) {
                is LoggedIn -> ViewState(
                    false,
                    state.toString()
                )
                is NotLoggedIn -> ViewState(
                    true,
                    state.toString()
                )
                is Error -> ViewState(
                    false,
                    state.toString()
                )
            }.let {
                viewState.postValue(it)
            }
        }))
    }

    private fun handleEffects(effects: Set<Effect<*>>) {
        effects.forEach { effect ->
            when (effect) {
                is LogInEffect -> {
                    appCoroutineScope.launch(Dispatchers.Default) {
                        effect.flow.collect {
                            when (it) {
                                is Success -> handleAction(LoggedInAction(it.data))
                                is Failure -> handleAction(ErrorAction(it.exception))
                            }
                        }
                    }
                }
                is MakeToastEffect -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        effect.flow.collect {}
                    }
                }
                is UpdateViewStateEffect -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        effect.flow.collect {}
                    }
                }
            } as Any?
        }
    }

    private fun getAppSubState(): Result<MapVmAppSubState> {
        return vmAppSubState?.let { Success(it) } ?: Failure(Exception("Failed to get app state"))
    }

    private fun reducer(
        appSubState: MapVmAppSubState,
        state: State,
        action: Action
    ): ReducerResult<State, Effect<*>> {
        return when (action) {
            is TextChangedAction -> {
                when (state) {
                    is NotLoggedIn -> state.copy(currentUsername = action.text).let {
                        ReducerResult(it, setOf())
                    }
                    is LoggedIn, is Error -> {
                        Error(IllegalActionException(action, state)).let {
                            ReducerResult(it, setOf())
                        }
                    }
                }
            }
            LoginClickedAction -> {
                when (state) {
                    is NotLoggedIn -> ReducerResult(
                        state,
                        setOf(
                            LogInEffect(appSubState.loginUseCase.flow())
                        ))
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
            is LoggedInAction -> {
                when(state) {
                    is NotLoggedIn -> {
                        LoggedIn(action.username).let {
                                ReducerResult(it, setOf())
                        }
                    }
                    is LoggedIn, is Error -> {
                        Error(IllegalActionException(action, state)).let {
                            ReducerResult(it, setOf())
                        }
                    }
                }
            }
        }
    }

    private fun makeToast(text: String): Flow<Result<Any>> {
        return flow {
            toastEvent.postValue(text)
        }
    }

}


sealed class Action
class ErrorAction(val exception: Exception) : Action()
class TextChangedAction(val text: String) : Action()
class LoggedInAction(val username: String) : Action()

object LoginClickedAction : Action()

sealed class State
data class NotLoggedIn(val currentUsername: String?) : State()
data class LoggedIn(val username: String) : State()
data class Error(val exception: Exception) : State()

sealed class Effect<T>(val flow: Flow<T>)
class LogInEffect(flow: Flow<Result<String>>) : Effect<Result<String>>(flow)
class MakeToastEffect(flow: Flow<Result<Any>>) : Effect<Result<Any>>(flow)
class UpdateViewStateEffect(flow: Flow<Result<Any>>) : Effect<Result<Any>>(flow)

class ViewState(
    val loginViewsVisible: Boolean,
    val textState: String
)

// the part of app state that is needed for vm
// dependencies scopes are part of the state
class MapVmAppSubState(
    val userState: UserState,
    val loginUseCase: LoginUseCase
)

open class ReducerResult<S, E>(val newState: S, val effects: Set<E>) {
    constructor(newState: S) : this(newState, setOf())

    fun mapEffects(mapper: (Set<E>) -> Set<E>): ReducerResult<S, E> {
        return ReducerResult(this.newState, mapper.invoke(this.effects))
    }
}

class IllegalActionException(action: Any, state: Any) :
    IllegalStateException("Illegal action $action for state $state")
