package com.velnox.app.core.customer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.velnox.app.VelnoxApplication

/** The process-wide [CustomerRepository], for composables that run inside the real app. */
@Composable
fun rememberCustomerRepository(): CustomerRepository =
    (LocalContext.current.applicationContext as VelnoxApplication).customer
