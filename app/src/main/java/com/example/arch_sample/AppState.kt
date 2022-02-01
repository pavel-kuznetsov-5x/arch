package com.example.arch_sample

sealed class AppState
class Initial(val appScope: AppScope, val userState: UserState): AppState()

sealed class UserState
object UserNotLoggedIn : UserState()
class UserLoggedIn(
    val userScope: UserScope,
    val username: String,
    val token: String
) : UserState()
