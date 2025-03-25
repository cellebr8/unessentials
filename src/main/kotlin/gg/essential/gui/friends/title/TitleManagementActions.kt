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
package gg.essential.gui.friends.title

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialCollapsibleSearchbar
import gg.essential.gui.common.modal.CancelableInputModal
import gg.essential.gui.common.modal.UsernameInputModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.friends.SocialMenu
import gg.essential.gui.modals.select.offlinePlayers
import gg.essential.gui.modals.select.onlinePlayers
import gg.essential.gui.modals.select.selectModal
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.iconAndMarkdownBody
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.network.connectionmanager.relationship.FriendRequestState
import gg.essential.network.connectionmanager.relationship.RelationshipErrorResponse
import gg.essential.network.connectionmanager.relationship.RelationshipResponse
import gg.essential.network.connectionmanager.relationship.message
import gg.essential.util.GuiUtil
import gg.essential.util.colored
import gg.essential.util.thenAcceptOnMainThread
import java.util.*
import java.util.concurrent.CompletableFuture

abstract class TitleManagementActions(private val gui: SocialMenu) : UIContainer() {

    abstract val search: EssentialCollapsibleSearchbar

    protected fun addFriend() {
        GuiUtil.pushModal { manager ->
            AddFriendModal(manager) { uuid, username, modal ->
                val future = gui.socialStateManager.relationshipStates.addFriend(uuid, false)
                consumeRelationshipFutureFromModal(
                    modal, future
                ) {
                    Notifications.push("", "") {
                        iconAndMarkdownBody(
                            EssentialPalette.ENVELOPE_9X7.create(),
                            "Friend request sent to ${username.colored(EssentialPalette.TEXT_HIGHLIGHT)}"
                        )
                    }
                }
            }
        }
    }

    protected fun makeGroup() {
        GuiUtil.launchModalFlow {
            makeGroupModal(gui)
        }
    }

    protected fun blockPlayer() {
        GuiUtil.pushModal { manager ->
            BlockPlayerModal(manager) { uuid, username, modal ->
                val future = gui.socialStateManager.relationshipStates.blockPlayer(uuid, false)
                consumeRelationshipFutureFromModal(
                    modal, future
                ) {
                    Notifications.push("", "") {
                        iconAndMarkdownBody(
                            EssentialPalette.BLOCK_7X7.create(),
                            "${username.colored(EssentialPalette.TEXT_HIGHLIGHT)} has been blocked"
                        )
                    }
                }
            }
        }
    }

    // Adapted from RelationshipStateManagerImpl consumeRelationshipFuture
    private fun consumeRelationshipFutureFromModal(
        modal: UsernameInputModal,
        future: CompletableFuture<RelationshipResponse>,
        onSuccess: () -> Unit
    ) {
        future.thenAcceptOnMainThread {
            modal.primaryButtonEnableStateOverride.set(true)
            when (it.friendRequestState) {
                FriendRequestState.SENT -> {
                    onSuccess()
                    modal.replaceWith(null)
                }

                FriendRequestState.ERROR_HANDLED, FriendRequestState.ERROR_UNHANDLED -> {
                    modal.errorOverride.set(
                        if (it.relationshipErrorResponse == RelationshipErrorResponse.TARGET_NOT_EXIST) {
                            "Not an Essential user"
                        } else {
                            it.message
                        }
                    )
                }
            }
        }.whenComplete { _, _ ->
            // Always re-enable the button when we complete the future
            modal.primaryButtonEnableStateOverride.set(true)
        }
    }

    class AddFriendModal(
        modalManager: ModalManager,
        whenValidated: (UUID, String, UsernameInputModal) -> Unit,
    ) : UsernameInputModal(modalManager, "", whenValidated = whenValidated) {
        init {
            configure {
                primaryButtonText = "Add"
                titleText = "Add Friend"
                contentText = "Enter a Minecraft username\nto add them as a friend."
            }
        }
    }

    class BlockPlayerModal(
        modalManager: ModalManager,
        whenValidated: (UUID, String, UsernameInputModal) -> Unit,
    ) : UsernameInputModal(modalManager, "", whenValidated = whenValidated) {
        init {
            configure {
                primaryButtonText = "Block"
                titleText = "Block Player"
                contentText = "Enter a Minecraft username\nto block them."
            }
        }
    }

    companion object {
        suspend fun ModalFlow.makeGroupModal(socialMenu: SocialMenu) {
            while (true) {
                val friends = selectFriendsForGroupModal() ?: return
                val name = enterGroupNameModal() ?: continue
                socialMenu.socialStateManager.messengerStates.createGroup(friends, name).thenAcceptOnMainThread {
                    // Intentionally delayed one frame so that the channel preview callback can fire first
                    Window.enqueueRenderOperation {
                        socialMenu.openMessageScreen(it)
                    }
                }
                return
            }
        }

        suspend fun ModalFlow.enterGroupNameModal(): String? {
            return awaitModal { continuation ->
                CancelableInputModal(modalManager, "", "", maxLength = 24).configure {
                    titleText = "Make Group"
                    contentText = "Enter a name for your group."
                    primaryButtonText = "Make Group"
                    titleTextColor = EssentialPalette.TEXT_HIGHLIGHT

                    cancelButtonText = "Back"

                    mapInputToEnabled { it.isNotBlank() }
                    onPrimaryActionWithValue { result -> replaceWith(continuation.resumeImmediately(result)) }
                    onCancel { button -> if (button) replaceWith(continuation.resumeImmediately(null)) }
                }
            }
        }

        suspend fun ModalFlow.selectFriendsForGroupModal(): Set<UUID>? {
            return selectModal("Select Friends") {
                requiresSelection = true
                requiresButtonPress = false

                onlinePlayers()
                offlinePlayers()

                modalSettings {
                    primaryButtonText = "Continue"
                    cancelButtonText = "Cancel"
                }
            }
        }
    }

}
