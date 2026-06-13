package com.example.meetloggerv2.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.meetloggerv2.data.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val subscription: String = "free"
) {
    fun toUser(): User = User(id, name, email, photoUrl, subscription)

    companion object {
        fun fromUser(user: User): UserEntity = UserEntity(
            id = user.id,
            name = user.name,
            email = user.email,
            photoUrl = user.photoUrl,
            subscription = user.subscription
        )
    }
}
