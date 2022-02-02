package com.example.arch_sample.use_case

import com.example.arch_sample.util.Failure
import com.example.arch_sample.util.Result
import com.example.arch_sample.util.Success
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.coroutines.CoroutineContext

class SaveUserDataUseCase() {
    fun flow(data: UserData): Flow<Result<Any>> {
        return flowOf(Success(true))
    }
}

