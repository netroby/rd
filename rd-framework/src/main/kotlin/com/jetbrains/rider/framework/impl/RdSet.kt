package com.jetbrains.rider.framework.impl

import com.jetbrains.rider.framework.*
import com.jetbrains.rider.framework.base.*
import com.jetbrains.rider.util.lifetime.Lifetime
import com.jetbrains.rider.util.reactive.AddRemove
import com.jetbrains.rider.util.reactive.IMutableViewableSet
import com.jetbrains.rider.util.reactive.IViewableSet
import com.jetbrains.rider.util.reactive.ViewableSet
import com.jetbrains.rider.util.string.PrettyPrinter
import com.jetbrains.rider.util.string.print
import com.jetbrains.rider.util.string.printToString
import com.jetbrains.rider.util.trace


class RdSet<T : Any> private constructor(val valueSerializer: ISerializer<T>, private val set: ViewableSet<T>)
: RdReactiveBase(), IMutableViewableSet<T> by set {

    companion object {
//        override val _type : Class<*> get() = throw IllegalStateException("Mustn't be used for polymorphic marshalling")
        fun<T: Any> read(ctx: SerializationCtx, stream: AbstractBuffer, valueSerializer: ISerializer<T>): RdSet<T> = RdSet(valueSerializer).withId(RdId.read(stream))
        fun<T: Any> write(ctx: SerializationCtx, stream: AbstractBuffer, value: RdSet<T>) = value.rdid.write(stream)
    }


    override fun init(lifetime: Lifetime) {
        super.init(lifetime)

        val serializationContext = serializationContext

        localChange { advise(lifetime) lambda@{ kind, v ->
            if (!isLocalChange) return@lambda

            wire.send(rdid, { buffer ->
                buffer.writeEnum(kind)
                valueSerializer.write(serializationContext, buffer, v)

                logSend.trace { "set `${location()}` ($rdid) :: $kind :: ${v.printToString()} "}
            })
        }}

        wire.advise(lifetime, rdid) { buffer ->
            val kind = buffer.readEnum<AddRemove>()
            val v = valueSerializer.read(serializationContext, buffer)

            //todo maybe identify is forgotten

            when (kind) {
                AddRemove.Add -> set.add(v)
                AddRemove.Remove -> set.remove(v)
                else -> throw IllegalStateException(kind.toString())
            }
        }
    }


    constructor(valueSerializer: ISerializer<T> = Polymorphic<T>()) : this(valueSerializer, ViewableSet())



    override fun print(printer: PrettyPrinter) {
        super.print(printer)
        printer.print(" [")
        if (!isEmpty()) printer.println()

        printer.indent {
            forEach {
                it.print(printer)
                printer.println()
            }
        }
        printer.print("]")
    }



    override fun add(element: T): Boolean {
        return localChange { set.add(element) }
    }

    override fun addAll(elements: Collection<T>): Boolean {
        return localChange { set.addAll(elements) }
    }

    override fun iterator(): MutableIterator<T> {
        val delegate = set.iterator()
        return object:MutableIterator<T> by delegate {
            override fun remove() {
                localChange { delegate.remove() }
            }
        }
    }

    override fun remove(element: T): Boolean {
        return localChange { set.remove(element) }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        return localChange { set.removeAll(elements) }
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        return localChange { set.retainAll(elements) }
    }

    override fun clear() = localChange { set.clear() }


    override fun advise(lifetime: Lifetime, handler: (IViewableSet.Event<T>) -> Unit) {
        if (isBound) assertThreading()
        set.advise(lifetime, handler)
    }


}
