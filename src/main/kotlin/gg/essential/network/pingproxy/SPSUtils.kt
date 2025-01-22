/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.network.pingproxy

import com.google.gson.JsonParser
import gg.essential.Essential
import gg.essential.connectionmanager.common.packet.Packet
import gg.essential.connectionmanager.common.packet.pingproxy.ClientPingProxyPacket
import gg.essential.connectionmanager.common.packet.pingproxy.ServerPingProxyResponsePacket
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.util.MinecraftUtils
import java.util.*

fun fetchWorldNameFromSPSHost(uuid: UUID): State<String?> {
    val connectionManager = Essential.getInstance().connectionManager
    val address = connectionManager.spsManager.getSpsAddress(uuid)

    val worldName = mutableStateOf<String?>(null)
    connectionManager.send(ClientPingProxyPacket(address, 25565, MinecraftUtils.currentProtocolVersion)) { maybePacket: Optional<Packet> ->
        val packet = maybePacket.orElse(null) as? ServerPingProxyResponsePacket ?: return@send

        try {
            val json = JsonParser().parse(packet.rawJson).asJsonObject
            worldName.set(json.get("description").asJsonObject.get("text").asString.split(" - ").drop(1).joinToString(" - "))
        } catch (exception: Exception) {
            Essential.logger.warn("Failed to parse server info", exception)
        }
    }

    return worldName
}
