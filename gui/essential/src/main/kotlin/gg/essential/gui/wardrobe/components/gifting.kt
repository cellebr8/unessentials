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
@file:JvmName("Gifting")
package gg.essential.gui.wardrobe.components

import com.sparkuniverse.toolbox.chat.enums.ChannelType
import gg.essential.api.gui.NotificationType
import gg.essential.api.gui.Slot
import gg.essential.connectionmanager.common.packet.cosmetic.ClientCosmeticBulkRequestUnlockStatePacket
import gg.essential.connectionmanager.common.packet.cosmetic.ServerCosmeticBulkRequestUnlockStateResponsePacket
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket
import gg.essential.cosmetics.CosmeticId
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.plus
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.elementa.utils.ObservableList
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.modal.EssentialModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.add
import gg.essential.gui.elementa.state.v2.addAll
import gg.essential.gui.elementa.state.v2.collections.MutableTrackedList
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.not
import gg.essential.gui.elementa.state.v2.isNotEmpty
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.remove
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.modals.select.SelectModal
import gg.essential.gui.modals.select.selectModal
import gg.essential.gui.modals.select.users
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.content.CosmeticPreviewToastComponent
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.Item
import gg.essential.gui.wardrobe.ItemId
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.giftCosmeticOrEmote
import gg.essential.gui.wardrobe.modals.CoinsPurchaseModal
import gg.essential.gui.wardrobe.modals.StoreDisabledModal
import gg.essential.network.connectionmanager.coins.CoinsManager
import gg.essential.network.connectionmanager.features.Feature
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.universal.ChatColor
import gg.essential.universal.USound
import gg.essential.util.CachedAvatarImage
import gg.essential.util.Client
import gg.essential.util.EssentialSounds
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.UuidNameLookup
import gg.essential.vigilance.utils.onLeftClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import java.util.UUID

private val LOGGER = LoggerFactory.getLogger("Essential Logger")

fun openGiftModal(item: Item.CosmeticOrEmote, state: WardrobeState) {
    if (platform.disabledFeaturesManager.isFeatureDisabled(Feature.COSMETIC_PURCHASE)) {
        platform.pushModal { StoreDisabledModal(it) }
        return
    }

    val requiredCoinsSpent = state.settings.giftingCoinSpendRequirement.get()
    val coinsSpent = state.coinsSpent.get()
    if (coinsSpent < requiredCoinsSpent) {
        launchModalFlow(platform.createModalManager()) {
            cannotGiftYetModal(requiredCoinsSpent)
        }
        return
    }

    // Get all friends except those who already own the item to gift
    val allFriends = platform.createSocialStates().relationships.getObservableFriendList()
    val validFriends = mutableListStateOf<UUID>()
    val loadingFriends = mutableStateOf(allFriends.isNotEmpty())

    platform.cmConnection.send(ClientCosmeticBulkRequestUnlockStatePacket(allFriends.toSet(), item.cosmetic.id)) { maybePacket ->
        ServerCosmeticBulkRequestUnlockStateResponsePacket::class.java // FIXME workaround for feature-flag-processor eating the packet
        when (val packet = maybePacket.orElse(null)) {
            is ServerCosmeticBulkRequestUnlockStateResponsePacket -> validFriends.addAll(packet.unlockStates.filter { !it.value }.keys.toList())
            else -> {
                showErrorToast("Something went wrong, please try again.")
                val prefix = (packet as? ResponseActionPacket)?.let { "$it - " } ?: ""
                LOGGER.error(prefix + "Failed to validate unlock status for ${item.cosmetic.displayName} in friends list.")
            }
        }
        loadingFriends.set(false)
    }

    platform.pushModal { manager ->
        createSelectFriendsToGiftModal(manager, item, state, allFriends, validFriends, loadingFriends).apply {
            onPrimaryAction { selectedUsers ->
                giftItemToFriends(item, selectedUsers, state, this)
            }
        }
    }
}

fun openWardrobeWithHighlight(itemId: ItemId) {
    platform.openWardrobe(itemId)
}

private fun showErrorToast(message: String) {
    Notifications.push("Gifting failed", message) { type = NotificationType.ERROR }
}

private fun giftItemToFriends(item: Item.CosmeticOrEmote, uuids: Set<UUID>, state: WardrobeState, modal: EssentialModal) {
    val cost = (item.getCost(state).get() ?: 0) * uuids.size
    if (cost > state.coins.get()) {
        modal.replaceWith(CoinsPurchaseModal.create(modal.modalManager, state, cost))
        return
    }

    for (uuid in uuids) {
        UuidNameLookup.getName(uuid).whenCompleteAsync({ username, exception ->
            if (exception != null) {
                showErrorToast("Something went wrong, please try again.")
                LOGGER.error("Failed to lookup username for $uuid", exception)
                return@whenCompleteAsync
            }

            state.giftCosmeticOrEmote(item, uuid) { success, errorCode ->
                if (!success) {
                    val errorMessage = when (errorCode) {
                        "TARGET_MUST_BE_FRIEND" -> "$username is not your friend!"
                        "ESSENTIAL_USER_NOT_FOUND" -> "$username is not an Essential user!"
                        "Cosmetic already unlocked." -> "$username already owns this item!"
                        else -> "Something went wrong gifting to $username, please try again."
                    }
                    showErrorToast(errorMessage)
                    return@giftCosmeticOrEmote
                }
                modal.replaceWith(null)
                EssentialSounds.playPurchaseConfirmationSound()
                showGiftSentToast(item.cosmetic, username)
                sendGiftEmbed(uuid, item.cosmetic.id)
            }
        }, Dispatchers.Client.asExecutor())
    }
}

fun showGiftSentToast(cosmetic: Cosmetic, username: String) {
    Notifications.push("", "${ChatColor.WHITE + cosmetic.displayName + ChatColor.RESET} has been gifted to $username.") {
        withCustomComponent(Slot.ACTION, CosmeticPreviewToastComponent(cosmetic))
    }
}

fun showGiftReceivedToast(cosmetic: Cosmetic, uuid: UUID, username: String) {
    Notifications.push(username, "", 4f, {
        openWardrobeWithHighlight(ItemId.CosmeticOrEmote(cosmetic.id))
    }) {
        withCustomComponent(Slot.ICON, CachedAvatarImage.create(uuid))
        withCustomComponent(Slot.SMALL_PREVIEW, CosmeticPreviewToastComponent(cosmetic))
        withCustomComponent(Slot.LARGE_PREVIEW, UIContainer().apply {
            val colorModifier = Modifier.color(EssentialPalette.TEXT).shadow(EssentialPalette.TEXT_SHADOW_LIGHT)
            layout(Modifier.fillWidth().childBasedHeight()) {
                wrappedText("{gift} Sent you a gift", Modifier.alignHorizontal(Alignment.Start), colorModifier) {
                    "gift" { icon(EssentialPalette.WARDROBE_GIFT_7X, colorModifier) }
                }
            }
        })
    }
}

fun createSelectFriendsToGiftModal(
    manager: ModalManager,
    item: Item.CosmeticOrEmote,
    state: WardrobeState,
    allFriends: ObservableList<UUID>,
    validFriends: State<MutableTrackedList<UUID>>,
    loadingFriends: State<Boolean>,
): SelectModal<UUID> {
    fun LayoutScope.addRemoveCheckbox(selected: MutableState<Boolean>) {
        val hoverColor = selected.map { if (it) EssentialPalette.CHECKBOX_SELECTED_BACKGROUND_HOVER else EssentialPalette.CHECKBOX_BACKGROUND_HOVER }
        val colorModifier = Modifier.color(EssentialPalette.CHECKBOX_BACKGROUND)
            .whenTrue(!selected, Modifier.outline(EssentialPalette.CHECKBOX_OUTLINE, 1f, true))
            .whenTrue(selected, Modifier.color(EssentialPalette.CHECKBOX_SELECTED_BACKGROUND))

        box(colorModifier.width(9f).heightAspect(1f).hoverScope().hoverColor(hoverColor)) {
            if_(selected) {
                image(EssentialPalette.CHECKMARK_7X5, Modifier.color(EssentialPalette.TEXT_HIGHLIGHT))
            }
        }
    }

    val selectedFriends = mutableListStateOf<UUID>()

    return selectModal(manager, "Select friends to\ngift them ${ChatColor.WHITE + item.name + ChatColor.RESET}.") {
        modalSettings {
            primaryButtonText = "Purchase"
            titleTextColor = EssentialPalette.TEXT
        }

        emptyText(loadingFriends.map { loading ->
            when {
                allFriends.isEmpty() -> "You haven't added any friends yet. You can add them in the social menu."
                loading -> "Loading..."
                else -> "Your friends already own this item."
            }
        })

        requiresButtonPress = false
        requiresSelection = true

        users("Friends", validFriends) {selected, uuid ->
            box(Modifier.fillParent()) {
                row(Modifier.fillParent(padding = 3f)) {
                    playerEntry(selected, uuid)
                    addRemoveCheckbox(selected)
                }
            }.onLeftClick { event ->
                USound.playButtonPress()
                event.stopPropagation()
                selected.set { !it }
            }
        }

        extraContent = {
            val quantityText = selectedFriends.map { "${it.size}x ${item.name}" }
            val costText = memo { CoinsManager.COIN_FORMAT.format((item.getCost(state)() ?: 0) * selectedFriends().size) }
            val shadowModifier = Modifier.shadow(EssentialPalette.BLACK)

            if_(validFriends.isNotEmpty()) {
                column(Modifier.fillWidth(rightPadding = 1f)) {
                    spacer(height = 10f)
                    row(Modifier.fillWidth(), Arrangement.SpaceBetween) {
                        text(quantityText, shadowModifier.color(EssentialPalette.TEXT_MID_GRAY))
                        row(Arrangement.spacedBy(2f)) {
                            text(costText, shadowModifier.color(EssentialPalette.TEXT))
                            image(EssentialPalette.COIN_7X, shadowModifier)
                        }
                    }
                    spacer(height = 1f)
                }
            }
        }
    }.onSelection {uuid, selected ->
        if (selected) {
            selectedFriends.add(uuid)
        } else {
            selectedFriends.remove(uuid)
        }
    }
}

suspend fun ModalFlow.cannotGiftYetModal(requiredCoinsSpent: Int) {
    awaitModal<Unit> {
        val modal = EssentialModal(modalManager, false).configure {
            titleText = "You can't gift yet..."
            titleTextColor = EssentialPalette.MODAL_WARNING
            primaryButtonText = "Okay!"
        }

        val textModifier = Modifier.color(EssentialPalette.TEXT).shadow(EssentialPalette.BLACK)

        modal.configureLayout { customContent ->
            customContent.layoutAsColumn {
                spacer(height = 13f)
                text("You can only send gifts", textModifier)
                spacer(height = 4f)
                row {
                    text("after spending $requiredCoinsSpent ", textModifier)
                    spacer(width = 1f)
                    image(EssentialPalette.COIN_7X)
                }
                spacer(height = 17f)
            }
        }
        modal
    }
}

private fun sendGiftEmbed(receiver: UUID, cosmeticId: CosmeticId) {
    val messages = platform.createSocialStates().messages
    val channel = messages.getObservableChannelList().find { it.type == ChannelType.DIRECT_MESSAGE && receiver in it.members } ?: return
    val url = HttpUrl.Builder()
        .scheme("https")
        .host("essential.gg")
        .addPathSegment("gift")
        .addPathSegment(cosmeticId)
        .build()
    messages.sendMessage(channel.id, url.toString())
}
