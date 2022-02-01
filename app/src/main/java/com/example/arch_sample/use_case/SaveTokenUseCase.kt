package com.example.arch_sample.use_case

import com.example.arch_sample.util.Failure
import com.example.arch_sample.util.Result
import com.example.arch_sample.util.Success
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext

class SaveTokenUseCase() {
    fun flow(token: String): Flow<Result<Any>> {
        return flow { Success(true) }
    }
}

