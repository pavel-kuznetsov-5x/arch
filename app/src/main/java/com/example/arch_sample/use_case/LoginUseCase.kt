package com.example.arch_sample.use_case

import com.example.arch_sample.util.Failure
import com.example.arch_sample.util.Result
import com.example.arch_sample.util.Success
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@FlowPreview
class LoginUseCase(
    private val api: Api,
    private val saveTokenUseCase: SaveTokenUseCase
) {
    fun flow(): Flow<Result<String>> {
        return flow<Result<String>> {
            api.logIn()?.let {
                Success(it)
            } ?: Failure(NullPointerException())
        }.flatMapConcat { loginResult ->
            when (loginResult) {
                is Success -> {
                    saveTokenUseCase.flow(loginResult.data).map { Success(loginResult.data) }
                }
                is Failure -> {
                    flow {}
                }
            }
        }
    }
}

class Api {
    suspend fun logIn(): String? = "token" as String?
}
