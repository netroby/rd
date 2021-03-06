package com.jetbrains.rider.framework.test.cases

import com.jetbrains.rider.framework.RdTaskResult
import com.jetbrains.rider.framework.base.static
import com.jetbrains.rider.framework.impl.RdCall
import com.jetbrains.rider.framework.impl.RdEndpoint
import com.jetbrains.rider.framework.isFaulted
import com.jetbrains.rider.util.reactive.valueOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class RdTaskTest : RdTestBase() {
    @Test
    fun testStaticSuccess() {
        val entity_id = 1

        val client_entity = RdCall<Int, String>().static(entity_id)
        val server_entity = RdEndpoint(Int::toString).static(entity_id)

        //not bound
        assertFails { client_entity.sync(0) }
        assertFails { client_entity.start(0) }

        clientProtocol.bindStatic(client_entity, "top")
        serverProtocol.bindStatic(server_entity, "top")

        assertEquals("0", client_entity.sync(0))
        assertEquals("1", client_entity.sync(1))

        val taskResult = (client_entity.start(2).result.valueOrThrow as RdTaskResult.Success<String>)
        assertEquals("2", taskResult.value)
    }

    @Test
    fun testStaticFailure() {
        val entity_id = 1

        val client_entity = RdCall<Int, String>().static(entity_id)
        val server_entity = RdEndpoint<Int, String>({ _ -> throw IllegalStateException("1234")}).static(entity_id)


        clientProtocol.bindStatic(client_entity, "top")
        serverProtocol.bindStatic(server_entity, "top")

        val task = client_entity.start(2)
        assertTrue { task.isFaulted }

        val taskResult = (task.result.valueOrThrow as RdTaskResult.Fault)
        assertEquals("1234", taskResult.error.reasonMessage)
        assertEquals("IllegalStateException", taskResult.error.reasonTypeFqn)

    }
}


