package com.xmobile.appclientmonitoringposturebot.util

import android.util.Patterns

object ValidateUtil {
    fun emptyCheckEmail(email: String): Boolean {
        return email.isNotEmpty()
    }

    fun emptyCheckPassword(password: String): Boolean {
        return password.isNotEmpty()
    }

    fun formatCheck(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun emptyCheckUserName(userName: String): Boolean {
        return userName.isNotEmpty()
    }

    fun conditionCheckPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun conditionCheckMatching(password: String, confirmPassword: String): Boolean {
        return password == confirmPassword
    }

    fun conditionCheckUserName(userName: String): Boolean {
        return userName.length >= 6
    }

    fun conditionCheckGoal(goal: Int): Boolean {
        return goal < 24
    }
}