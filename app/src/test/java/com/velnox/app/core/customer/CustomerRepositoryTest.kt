package com.velnox.app.core.customer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerRepositoryTest {

    private class Api(private val answer: CustomerResult<List<CustomerAddress>>) : FakeCustomerApi() {
        var seenToken: String? = null
        override suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>> {
            seenToken = token
            return answer
        }
    }

    @Test
    fun `a guest never reaches the network and does not trigger the 401 handler`() = runBlocking {
        val api = Api(CustomerResult.Ok(emptyList()))
        var unauthorized = 0
        val repository = CustomerRepository(api, token = { null }, onUnauthorized = { unauthorized++ })

        val result = repository.call { a, t -> a.addresses(t) }

        assertEquals(CustomerResult.Failed(CustomerError.Unauthorized), result)
        assertEquals(null, api.seenToken)
        assertEquals(0, unauthorized)
    }

    @Test
    fun `the session token is attached to the call`() = runBlocking {
        val api = Api(CustomerResult.Ok(emptyList()))
        val repository = CustomerRepository(api, token = { "tok-1" })

        repository.call { a, t -> a.addresses(t) }

        assertEquals("tok-1", api.seenToken)
    }

    @Test
    fun `a 401 from the server runs the handler and is still returned`() = runBlocking {
        val api = Api(CustomerResult.Failed(CustomerError.Unauthorized))
        var unauthorized = 0
        val repository = CustomerRepository(api, token = { "dead" }, onUnauthorized = { unauthorized++ })

        val result = repository.call { a, t -> a.addresses(t) }

        assertEquals(CustomerResult.Failed(CustomerError.Unauthorized), result)
        assertEquals(1, unauthorized)
    }

    @Test
    fun `other failures do not touch the session`() = runBlocking {
        val api = Api(CustomerResult.Failed(CustomerError.Network))
        var unauthorized = 0
        val repository = CustomerRepository(api, token = { "tok" }, onUnauthorized = { unauthorized++ })

        repository.call { a, t -> a.addresses(t) }

        assertEquals(0, unauthorized)
    }
}
