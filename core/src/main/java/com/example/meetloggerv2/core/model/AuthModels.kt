package com.example.meetloggerv2.core.model

data class CheckEmailResponse(
    val exists: Boolean,
    val methods: List<String>
)
