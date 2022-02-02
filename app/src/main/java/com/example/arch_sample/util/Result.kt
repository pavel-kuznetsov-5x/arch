package com.example.arch_sample.util

sealed class Result<T> {
    fun <E> map(mapFunction: (T) -> E): Result<E> {
        return when (this) {
            is Success -> Success(mapFunction(this.data))
            is Failure -> Failure(this.exception)
        }
    }
}

class Success<T>(val data: T) : Result<T>()
class Failure<T>(val exception: Exception) : Result<T>()


