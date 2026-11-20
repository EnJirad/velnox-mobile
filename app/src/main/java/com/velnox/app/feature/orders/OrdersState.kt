package com.velnox.app.feature.orders

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.R
import com.velnox.app.core.customer.CustomerOrder
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.feature.cart.CartState
import com.velnox.app.ui.components.StatusTone

enum class OrderStatus(@StringRes val labelRes: Int, val tone: StatusTone) {
    Pending(R.string.orders_status_pending, StatusTone.Warning),
    Processing(R.string.orders_status_processing, StatusTone.Brand),
    Shipping(R.string.orders_status_shipping, StatusTone.Brand),
    Completed(R.string.orders_status_completed, StatusTone.Success),
    Cancelled(R.string.orders_status_cancelled, StatusTone.Neutral),
    Returned(R.string.orders_status_returned, StatusTone.Danger),
}

/** Filter tabs in the order in which the brief lists them. */
enum class OrdersFilter(@StringRes val labelRes: Int) {
    All(R.string.orders_tab_all),
    Pending(R.string.orders_tab_pending),
    Processing(R.string.orders_tab_processing),
    Shipping(R.string.orders_tab_shipping),
    Completed(R.string.orders_tab_completed),
    Cancelled(R.string.orders_tab_cancelled),
}

/** Map the server's raw status string to the UI status bucket. */
val CustomerOrder.uiStatus: OrderStatus
    get() = when (status.lowercase()) {
        "pending", "pending_payment", "awaiting_payment" -> OrderStatus.Pending
        "confirmed", "processing", "paid", "packing", "ready_to_ship" -> OrderStatus.Processing
        "shipped", "shipping", "in_transit", "out_for_delivery" -> OrderStatus.Shipping
        "delivered", "completed" -> OrderStatus.Completed
        "cancelled", "canceled", "expired", "failed" -> OrderStatus.Cancelled
        "returned", "refunded", "return_requested" -> OrderStatus.Returned
        else -> OrderStatus.Pending
    }

/**
 * Shared server-backed order state across Orders tab, Order Detail, and Profile.
 *
 * Starts empty (zero mock/demo orders) and mirrors `/api/customer/orders`.
 */
object OrdersState {

    val orders = mutableStateListOf<CustomerOrder>()

    var status by mutableStateOf(LoadStatus.Idle)
        private set

    val activeCount: Int
        get() = orders.count {
            it.uiStatus == OrderStatus.Pending ||
                it.uiStatus == OrderStatus.Processing ||
                it.uiStatus == OrderStatus.Shipping
        }

    fun forFilter(filter: OrdersFilter): List<CustomerOrder> = when (filter) {
        OrdersFilter.All -> orders.toList()
        OrdersFilter.Pending -> orders.filter { it.uiStatus == OrderStatus.Pending }
        OrdersFilter.Processing -> orders.filter { it.uiStatus == OrderStatus.Processing }
        OrdersFilter.Shipping -> orders.filter { it.uiStatus == OrderStatus.Shipping }
        OrdersFilter.Completed -> orders.filter { it.uiStatus == OrderStatus.Completed }
        OrdersFilter.Cancelled -> orders.filter {
            it.uiStatus == OrderStatus.Cancelled || it.uiStatus == OrderStatus.Returned
        }
    }

    /** Load all orders from `GET /api/customer/orders`. */
    suspend fun load(repository: CustomerRepository): CustomerResult<Unit> {
        status = LoadStatus.Loading
        return when (val result = repository.call { api, token -> api.orders(token) }) {
            is CustomerResult.Ok -> {
                orders.clear()
                orders.addAll(result.value)
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> {
                status = LoadStatus.Failed
                result
            }
        }
    }

    /** Fetch full detail via `GET /api/customer/orders/:id` and update the cached list entry. */
    suspend fun loadDetail(
        repository: CustomerRepository,
        orderId: String,
    ): CustomerResult<CustomerOrder> {
        val result = repository.call { api, token -> api.order(token, orderId) }
        if (result is CustomerResult.Ok) {
            val idx = orders.indexOfFirst { it.id == result.value.id }
            if (idx >= 0) {
                orders[idx] = result.value
            } else {
                orders.add(0, result.value)
            }
        }
        return result
    }

    /** Cancel via `PATCH /api/customer/orders/:id/cancel` and refresh from server. */
    suspend fun cancelOrder(
        repository: CustomerRepository,
        orderId: String,
    ): CustomerResult<Unit> {
        val cancelled = repository.call { api, token -> api.cancelOrder(token, orderId) }
        if (cancelled is CustomerResult.Failed) return cancelled
        return load(repository)
    }

    /** Re-add order items to cart via `POST /api/customer/reorder` and mirror the updated cart. */
    suspend fun reorder(
        repository: CustomerRepository,
        orderId: String,
        cartState: CartState,
    ): CustomerResult<Unit> {
        return when (val res = repository.call { api, token -> api.reorder(token, orderId) }) {
            is CustomerResult.Ok -> {
                cartState.applyRemote(res.value)
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> res
        }
    }

    fun clear() {
        orders.clear()
        status = LoadStatus.Idle
    }

    /** Test seam: put orders into a known state without the network. */
    fun reset(initial: List<CustomerOrder> = emptyList()) {
        orders.clear()
        orders.addAll(initial)
        status = if (initial.isEmpty()) LoadStatus.Idle else LoadStatus.Ready
    }
}
