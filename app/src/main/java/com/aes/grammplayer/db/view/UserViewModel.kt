package com.aes.grammplayer.db.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.User
import com.aes.grammplayer.db.repository.UserRepository
import kotlinx.coroutines.launch

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository(AppDatabase.getDatabase(application))

    val allUsers = repository.getAll()
    val validatedUser = repository.getValidatedUser()

    fun insert(user: User) = viewModelScope.launch { repository.insert(user) }
    fun update(user: User) = viewModelScope.launch { repository.update(user) }
    fun delete(user: User) = viewModelScope.launch { repository.delete(user) }
}