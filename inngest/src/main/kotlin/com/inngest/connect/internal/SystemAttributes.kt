package com.inngest.connect.internal

import com.inngest.connect.v1.ConnectProto
import java.lang.management.ManagementFactory

/** Best-effort system attributes reported in WORKER_CONNECT. */
internal object SystemAttributes {
    fun retrieve(): ConnectProto.SystemAttributes =
        ConnectProto.SystemAttributes
            .newBuilder()
            .setCpuCores(Runtime.getRuntime().availableProcessors())
            .setMemBytes(totalMemoryBytes())
            .setOs(System.getProperty("os.name")?.lowercase() ?: "unknown")
            .build()

    private fun totalMemoryBytes(): Long =
        try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val method = osBean.javaClass.getMethod("getTotalPhysicalMemorySize")
            method.isAccessible = true
            (method.invoke(osBean) as? Long) ?: Runtime.getRuntime().maxMemory()
        } catch (e: Exception) {
            // Not a HotSpot-compatible JVM or reflective access denied; fall
            // back to the JVM's own memory ceiling.
            Runtime.getRuntime().maxMemory()
        }
}
