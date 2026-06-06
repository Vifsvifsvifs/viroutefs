// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class XrayEngineRunner(private val tunFd: Int = 0) {
    @Volatile private var running = false
    private var coreController: Any? = null

    fun start(configJson: String): Result<Unit> = runCatching {
        if (running) return@runCatching
        val libClass = Class.forName("libv2ray.Libv2ray")
        invokeIfPresent(libClass, null, "initCoreEnv", "", "")
            ?: invokeIfPresent(libClass, null, "InitCoreEnv", "", "")
        val callback = createCallback()
        val controller = invokeFirstPresent(
            libClass,
            null,
            listOf("newCoreController", "NewCoreController"),
            callback,
        ) ?: error("AndroidLibXrayLite NewCoreController API was not found.")
        coreController = controller
        invokeFirstPresent(
            controller.javaClass,
            controller,
            listOf("startLoop", "StartLoop"),
            configJson,
            tunFd,
        ) ?: error("AndroidLibXrayLite StartLoop API was not found.")
        running = true
    }.onFailure {
        running = false
        coreController = null
    }

    fun stop() {
        val controller = coreController
        if (controller != null) {
            runCatching {
                invokeFirstPresent(controller.javaClass, controller, listOf("stopLoop", "StopLoop"))
            }
        }
        running = false
        coreController = null
    }

    fun isRunning(): Boolean = running

    private fun createCallback(): Any {
        val callbackInterface = Class.forName("libv2ray.CoreCallbackHandler")
        return Proxy.newProxyInstance(
            callbackInterface.classLoader,
            arrayOf(callbackInterface),
            XrayCallbackHandler,
        )
    }

    private object XrayCallbackHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            when (method.name) {
                "toString" -> return "ViRouteFS Xray callback"
                "hashCode" -> return System.identityHashCode(proxy)
                "equals" -> return proxy === args?.firstOrNull()
            }
            return when (method.returnType) {
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Boolean.TYPE -> false
                java.lang.Void.TYPE -> Unit
                else -> null
            }
        }
    }

    private fun invokeFirstPresent(
        ownerClass: Class<*>,
        receiver: Any?,
        names: List<String>,
        vararg args: Any,
    ): Any? {
        for (name in names) {
            val method = findMethod(ownerClass, name, args.size) ?: continue
            return method.invoke(receiver, *args.coerceFor(method.parameterTypes)) ?: Unit
        }
        return null
    }

    private fun invokeIfPresent(ownerClass: Class<*>, receiver: Any?, name: String, vararg args: Any): Any? {
        val method = findMethod(ownerClass, name, args.size) ?: return null
        return method.invoke(receiver, *args.coerceFor(method.parameterTypes)) ?: Unit
    }

    private fun findMethod(ownerClass: Class<*>, name: String, parameterCount: Int): Method? =
        ownerClass.methods.firstOrNull { candidate ->
            candidate.name == name && candidate.parameterTypes.size == parameterCount
        }

    private fun Array<out Any>.coerceFor(parameterTypes: Array<Class<*>>): Array<Any> = mapIndexed { index, value ->
        when (parameterTypes[index]) {
            java.lang.Integer.TYPE, Integer::class.java -> when (value) {
                is Number -> value.toInt()
                else -> value
            }
            java.lang.Long.TYPE, java.lang.Long::class.java -> when (value) {
                is Number -> value.toLong()
                else -> value
            }
            else -> value
        }
    }.toTypedArray()
}
