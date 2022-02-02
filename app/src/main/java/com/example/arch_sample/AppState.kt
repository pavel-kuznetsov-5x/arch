package com.example.arch_sample

import com.example.arch_sample.use_case.UserData

sealed class AppState
data class Initial(val appScope: AppScope, val userState: UserState): AppState()

sealed class UserState
object UserNotLoggedIn : UserState()
data class UserLoggedIn(
    val userScope: UserScope,
    val username: String,
    val token: String
) : UserState()

sealed class AppAction
class LoggedInAppAction(
    val userData: UserData,
): AppAction()
object LogOutAppAction: AppAction()

sealed class AppEffect
