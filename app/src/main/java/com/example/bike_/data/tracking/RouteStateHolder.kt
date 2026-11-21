package com.example.bike_.data.tracking

import com.example.bike_.domain.model.LoadedRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RouteStateHolder {
    private val _route = MutableStateFlow<LoadedRoute?>(null)
    val route: StateFlow<LoadedRoute?> = _route.asStateFlow()

    fun setRoute(route: LoadedRoute?) {
        _route.value = route
    }

    fun clear() {
        _route.value = null
    }
}
