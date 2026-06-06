package com.example.meetloggerv2.core.navigation

import androidx.fragment.app.Fragment

interface NavigationRouter {
    fun navigateToHome()
    fun navigateToAudioList()
    fun navigateToReportList()
    fun navigateToProfile()
    fun navigateToFileDetails(fileName: String)
    fun navigateToLogin()
    fun navigateToLegal(type: String)
    fun navigateToHelpSupport()
    fun navigateToSettings()
    fun navigateToSubscriptions()
}

fun Fragment.findNavigationRouter(): NavigationRouter? {
    return activity as? NavigationRouter
}
