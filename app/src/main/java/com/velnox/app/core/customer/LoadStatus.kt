package com.velnox.app.core.customer

/** Where a server-backed list is in its life: never loaded, loading, loaded, or failed. */
enum class LoadStatus { Idle, Loading, Ready, Failed }
