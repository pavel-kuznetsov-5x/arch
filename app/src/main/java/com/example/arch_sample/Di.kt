package com.example.arch_sample

import com.example.arch_sample.use_case.LoginUseCase

class AppScope(
    val loginUseCase: LoginUseCase
)

class UserScope()
