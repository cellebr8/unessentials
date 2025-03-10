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
package gg.essential.util

import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.cosmetics.EquippedCosmetic
import gg.essential.elementa.components.Window
import gg.essential.gui.common.UIPlayer
import gg.essential.gui.common.modal.Modal
import gg.essential.gui.elementa.essentialmarkdown.EssentialMarkdown
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.friends.message.v2.MessageRef
import gg.essential.gui.friends.state.SocialStates
import gg.essential.gui.notification.NotificationsManager
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.overlay.OverlayManager
import gg.essential.gui.screenshot.bytebuf.LimitedAllocator
import gg.essential.gui.wardrobe.ItemId
import gg.essential.handlers.MojangSkinManager
import gg.essential.mod.Skin
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.preview.PerspectiveCamera
import gg.essential.model.backend.RenderBackend
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.cosmetics.AssetLoader
import gg.essential.network.connectionmanager.cosmetics.ModelLoader
import gg.essential.network.connectionmanager.features.DisabledFeaturesManager
import gg.essential.network.connectionmanager.media.IScreenshotManager
import gg.essential.network.connectionmanager.notices.INoticesManager
import gg.essential.network.connectionmanager.skins.SkinsManager
import gg.essential.universal.UImage
import gg.essential.universal.utils.ReleasedDynamicTexture
import gg.essential.util.image.bitmap.MutableBitmap
import gg.essential.util.lwjgl3.Lwjgl3Loader
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.CoroutineDispatcher
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import kotlin.jvm.Throws

interface GuiEssentialPlatform {
    val mcVersion: Int

    val clientThreadDispatcher: CoroutineDispatcher
    val renderThreadDispatcher: CoroutineDispatcher

    val renderBackend: RenderBackend
    val overlayManager: OverlayManager
    val assetLoader: AssetLoader
    val modelLoader: ModelLoader
    val lwjgl3: Lwjgl3Loader

    val cmConnection: CMConnection

    val notifications: NotificationsManager

    fun createModalManager(): ModalManager
    fun pushModal(builder: (ModalManager) -> Modal): Modal {
        val manager = createModalManager()
        val modal = builder(manager)
        manager.queueModal(modal)
        return modal
    }

    fun onResourceManagerReload(runnable: Runnable)

    @Throws(IOException::class)
    fun bitmapFromMinecraftResource(identifier: UIdentifier): MutableBitmap?

    @Throws(IOException::class)
    fun bitmapFromInputStream(inputStream: InputStream): MutableBitmap

    fun uImageIntoReleasedDynamicTexture(uImage: UImage): ReleasedDynamicTexture

    fun identifierFromTexture(texture: RenderBackend.Texture): UIdentifier

    fun bindTexture(textureUnit: Int, identifier: UIdentifier)

    fun playSound(identifier: UIdentifier)

    fun registerCosmeticTexture(name: String, texture: ReleasedDynamicTexture): UIdentifier

    fun dismissModalOnScreenChange(modal: Modal, dismiss: () -> Unit)

    val essentialBaseDir: Path
    val config: Config
    val pauseMenuDisplayWindow: Window

    val mcProtocolVersion: Int
    val mcGameVersion: String

    fun currentServerType(): ServerType?

    fun registerActiveSessionState(state: MutableState<USession>)

    fun createSocialStates(): SocialStates

    fun resolveMessageRef(messageRef: MessageRef)

    val essentialUriListener: EssentialMarkdown.(EssentialMarkdown.LinkClickEvent) -> Unit

    val noticesManager: INoticesManager

    val skinsManager: SkinsManager

    val mojangSkinManager: MojangSkinManager

    val screenshotManager: IScreenshotManager

    val disabledFeaturesManager: DisabledFeaturesManager

    val isOptiFineInstalled: Boolean

    val trustedHosts: Set<String>

    fun enqueueTelemetry(packet: ClientTelemetryPacket)

    fun trackByteBuf(alloc: LimitedAllocator, buf: ByteBuf): ByteBuf

    fun newGlFrameBuffer(width: Int, height: Int): GlFrameBuffer

    fun newUIPlayer(
        camera: State<PerspectiveCamera?>,
        profile: State<Pair<Skin, /*cape*/ String?>?>,
        cosmetics: State<Map<CosmeticSlot, EquippedCosmetic>>,
        sounds: State<Float>? = null,
        skinTextureOverride: UIdentifier? = null,
    ): UIPlayer

    fun openWardrobe(highlight: ItemId? = null)

    fun openSocialMenu(channelId: Long? = null)

    fun connectToServer(name: String, address: String)

    val openEmoteWheelKeybind: Keybind

    interface Keybind {
        val isBound: Boolean
        val boundKeyName: String?
        val isConflicting: Boolean
    }

    interface Config {
        val shouldDarkenRetexturedButtons: Boolean
        val useVanillaButtonForRetexturing: State<Boolean>
    }

    companion object {
        val platform: GuiEssentialPlatform =
            Class.forName(GuiEssentialPlatform::class.java.name + "Impl").newInstance() as GuiEssentialPlatform
    }
}
