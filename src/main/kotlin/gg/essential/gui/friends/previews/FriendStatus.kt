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
package gg.essential.gui.friends.previews

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint
import gg.essential.elementa.constraints.FillConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.state.State
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.common.shadow.ShadowIcon
import gg.essential.gui.common.state
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.friends.state.IStatusStates
import gg.essential.gui.friends.state.PlayerActivity
import gg.essential.network.pingproxy.fetchWorldNameFromSPSHost
import gg.essential.util.AddressUtil
import gg.essential.util.UUIDUtil
import java.util.*

class FriendStatus(
    uuid: UUID,
    statusStates: IStatusStates,
    sortListener: SortListener? = null
) : UIContainer() {

    init {
        constrain {
            width = 100.percent
            height = ChildBasedMaxSizeConstraint()
        }.apply {
            effect(ScissorEffect())
        }

        effect(this) {
            val activity = statusStates.getActivityState(uuid)()
            clearChildren()
            when (activity) {
                is PlayerActivity.Offline -> {
                    val lastSeenText = "Offline"
                    EssentialUIText(lastSeenText, shadowColor = EssentialPalette.BLACK, truncateIfTooSmall = true).constrain {
                        color = EssentialPalette.TEXT_DISABLED.toConstraint()
                    }
                }
                is PlayerActivity.Online -> {
                    EssentialUIText("Online", truncateIfTooSmall = true).constrain {
                        color = EssentialPalette.GREEN.toConstraint()
                    }
                }
                is PlayerActivity.OnlineWithDescription -> {
                    val description = if (activity.description == AddressUtil.SINGLEPLAYER) {
                        "In World"
                    } else {
                        activity.description
                    }
                    EssentialUIText(description, truncateIfTooSmall = true).constrain {
                        color = EssentialPalette.GREEN.toConstraint()
                    }
                }
                is PlayerActivity.Multiplayer -> {
                    createJoinableEntry(activity.serverAddress.state())
                }
                is PlayerActivity.SPSSession -> {
                    if (activity.invited) {
                        val worldName = fetchWorldNameFromSPSHost(uuid)
                        val username = UUIDUtil.nameState(activity.host)
                        createJoinableEntry(memo { worldName() ?: "${username()}'s world" }.toV1(this@FriendStatus))
                    } else {
                        EssentialUIText("In World", truncateIfTooSmall = true).constrain {
                            color = EssentialPalette.GREEN.toConstraint()
                        }
                    }
                }
            }.constrain {
                width = width.coerceAtMost(100.percent)
            } childOf this@FriendStatus

            sortListener?.sort()
        }
    }

    private fun createJoinableEntry(display: State<String>): UIComponent {
        val container by UIContainer().constrain {
            height = ChildBasedMaxSizeConstraint()
            width = 100.percent
        }
        ShadowIcon(EssentialPalette.JOIN_ARROW_5X, true).constrain {
            y = 2.pixels
        }.rebindPrimaryColor(EssentialPalette.MESSAGE_SENT.state()).rebindShadowColor(EssentialPalette.BLUE_SHADOW.state()) childOf container

        EssentialUIText(shadowColor = EssentialPalette.BLUE_SHADOW, truncateIfTooSmall = true).bindText(display).constrain {
            x = SiblingConstraint(3f)
            width = width.coerceAtMost(FillConstraint(false))
            color = EssentialPalette.MESSAGE_SENT.toConstraint()
        } childOf container
        return container
    }

}
