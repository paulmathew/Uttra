package com.uttra.app.onBoarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionViewModel(private val permissionChecker: PermissionChecker) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(PermissionStatus())

    val permissionStatus = _permissionStatus.asStateFlow()

    fun refreshPermissions() {
        _permissionStatus.value = permissionChecker.checkPermissions()
    }
}