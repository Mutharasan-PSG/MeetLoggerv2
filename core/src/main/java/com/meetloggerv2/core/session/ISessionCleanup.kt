package com.meetloggerv2.core.session

interface ISessionCleanup {
    suspend fun clearAllLocalData()
}
