package com.jetbrains.rider.framework.test.cases

import com.jetbrains.rider.framework.IProtocol
import com.jetbrains.rider.framework.Identities
import com.jetbrains.rider.framework.Protocol
import com.jetbrains.rider.framework.Serializers
import com.jetbrains.rider.framework.base.IRdBindable
import com.jetbrains.rider.framework.base.RdBindableBase
import com.jetbrains.rider.framework.base.static
import com.jetbrains.rider.framework.test.util.TestWire
import com.jetbrains.rider.util.Closeable
import com.jetbrains.rider.util.ILoggerFactory
import com.jetbrains.rider.util.Statics
import com.jetbrains.rider.util.lifetime.Lifetime
import com.jetbrains.rider.util.lifetime.LifetimeDefinition
import com.jetbrains.rider.util.log.ErrorAccumulatorLoggerFactory
import com.jetbrains.rider.util.reactive.IScheduler
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

object TestScheduler : IScheduler {
    override fun flush() {}
    override fun queue(action: () -> Unit) = action()
    override val isActive: Boolean get() = true
}

open class RdTestBase {
    private val serializers = Serializers()


    protected lateinit var clientProtocol: IProtocol
        private set


    protected lateinit var serverProtocol: IProtocol
        private set


    protected lateinit var clientLifetimeDef: LifetimeDefinition
        private set

    protected lateinit var serverLifetimeDef: LifetimeDefinition
        private set



    protected val clientWire : TestWire get() = clientProtocol.wire as TestWire
    protected val serverWire : TestWire get() = serverProtocol.wire as TestWire

    protected val clientLifetime : Lifetime get() = clientLifetimeDef.lifetime
    protected val serverLifetime : Lifetime get() = serverLifetimeDef.lifetime

    private var disposeLoggerFactory : Closeable? = null

    protected open val clientScheduler: IScheduler get() = TestScheduler
    protected open val serverScheduler: IScheduler get() = TestScheduler

    @BeforeTest
    fun setUp() {
        Statics<ILoggerFactory>().push(ErrorAccumulatorLoggerFactory)
        clientLifetimeDef = Lifetime.create(Lifetime.Eternal)
        serverLifetimeDef = Lifetime.create(Lifetime.Eternal)


        clientProtocol = Protocol(serializers,
                Identities(),
            clientScheduler, TestWire(clientScheduler))

        serverProtocol = Protocol(serializers,
                Identities(),
            serverScheduler, TestWire(serverScheduler))

        val (w1, w2) = (clientProtocol.wire as TestWire) to (serverProtocol.wire as TestWire)
        w1.counterpart = w2
        w2.counterpart = w1


    }

    @AfterTest
    fun tearDown() {
        disposeLoggerFactory?.close()
        disposeLoggerFactory = null


        clientLifetimeDef.terminate()
        serverLifetimeDef.terminate()
    }

    internal fun <T : IRdBindable> IProtocol.bindStatic(x: T, name: String): T {
        val lf = when (this) {
            clientProtocol -> clientLifetime
            serverProtocol -> serverLifetime
            else -> throw IllegalArgumentException("Not valid protocol, must be client or server")
        }
        x.bind(lf, this, name)
        return x
    }

    internal fun <T : RdBindableBase> IProtocol.bindStatic(x: T, id: Int): T {
        val lf = when (this) {
            clientProtocol -> clientLifetime
            serverProtocol -> serverLifetime
            else -> throw IllegalArgumentException("Not valid protocol, must be client or server")
        }
        x.static(id).bind(lf, this, "top")
        return x
    }


    protected fun setWireAutoFlush(flag: Boolean) {
        clientWire.autoFlush = flag
        serverWire.autoFlush = flag
    }
}