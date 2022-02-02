package com.example.arch_sample.use_case

import com.example.arch_sample.util.Failure
import com.example.arch_sample.util.Result
import com.example.arch_sample.util.Success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*

@FlowPreview
class LoginUseCase(
    private val appCoroutineScope: CoroutineScope,
    private val api: Api,
    private val saveUserDataUseCase: SaveUserDataUseCase
) {
    fun flow(username: String): Flow<Result<UserData>> {
        return suspend {
            api.logIn(username)?.let {
                Success(it)
            } ?: Failure(NullPointerException())
        }
            .asFlow()
            .map {
                it.map {
                    UserData(username, it)
                }
            }
            .flatMapConcat { loginResult ->
                when (loginResult) {
                    is Success -> {
                        saveUserDataUseCase.flow(loginResult.data).map { Success(loginResult.data) }
                    }
                    is Failure -> {
                        flowOf(loginResult)
                    }
                }
            }
    }
}

data class UserData(
    val username: String,
    val token: String
)

class Api {
    suspend fun logIn(username: String): String? = "token" as String?
}
