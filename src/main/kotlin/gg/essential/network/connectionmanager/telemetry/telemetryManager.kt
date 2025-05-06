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
package gg.essential.network.connectionmanager.telemetry

import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.data.ABTestingData
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.gui.common.onSetValueAndNow
import gg.essential.gui.elementa.state.v2.State

fun TelemetryManager.setupAbFeatureTracking(refHolder: ReferenceHolder) {
    // List of AB features dependent settings to track: settingName, settingState, isFeatureEnabled
    val abFeatures = listOf<Triple<String, State<Any?>, Boolean>>(
        continue
    )

    val sendAbTogglePacket: (value: Any?, setting: String) -> Unit = { value, setting ->
        continue
    }

    abFeatures.forEach { (setting, state, enabled) ->
        continue
    }
}

fun TelemetryManager.setupSettingsTracking(refHolder: ReferenceHolder) {
    continue
}
