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
package gg.essential.network.connectionmanager.media

import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.handlers.screenshot.ClientScreenshotMetadata
import gg.essential.media.model.Media
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.util.function.Consumer

interface IScreenshotManager {
    val screenshotMetadataManager: IScreenshotMetadataManager
    val uploadedMedia: Collection<Media>
    val orderedPaths: List<Path>

    fun setFavorite(path: Path, value: Boolean): ClientScreenshotMetadata
    fun setFavorite(path: Media, value: Boolean): ClientScreenshotMetadata

    fun registerScreenshotCollectionChangeHandler(handler: Consumer<ScreenshotCollectionChangeEvent>)
    fun handleScreenshotEdited(source: ScreenshotId, originalMetadata: ClientScreenshotMetadata, screenshot: BufferedImage, favorite: Boolean): File
}
