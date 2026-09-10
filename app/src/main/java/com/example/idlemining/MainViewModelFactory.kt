package com.example.idlemining

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.idlemining.ui.MainViewModel

class MainViewModelFactory(private val viewModel: MainViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
}
