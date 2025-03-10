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
package gg.essential.handlers;

import com.google.common.base.Suppliers;
import gg.essential.Essential;
import gg.essential.event.client.ReAuthEvent;
import gg.essential.mod.Model;
import gg.essential.mod.Skin;
import gg.essential.util.DispatchersKt;
import gg.essential.util.SkinKt;
import gg.essential.util.UUIDUtil;
import kotlinx.coroutines.Dispatchers;
import me.kbrewster.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static kotlinx.coroutines.ExecutorsKt.asExecutor;

public class McMojangSkinManager extends MojangSkinManager {

    private final GameProfileManager gameProfileManager;

    public McMojangSkinManager(GameProfileManager gameProfileManager, BooleanSupplier delayChanges) {
        super(delayChanges);

        this.gameProfileManager = gameProfileManager;
    }

    @Override
    protected void onReAuth(Runnable callback) {
        Essential.EVENT_BUS.register(new Object() {
            @Subscribe(priority = 2000)
            private void onReAuth(ReAuthEvent event) {
                callback.run();
            }
        });
    }

    @NotNull
    protected CompletableFuture<Skin> getSkinFromMinecraft() {
        return CompletableFuture.supplyAsync(
            //#if MC>=12002
            //$$ () -> MinecraftClient.getInstance().getGameProfile().getProperties(),
            //#else
            // Note: getProfileProperties is not thread-safe, so we must evaluate it immediately
            Suppliers.ofInstance(Minecraft.getMinecraft().getProfileProperties()),
            //#endif
            asExecutor(DispatchersKt.getClient(Dispatchers.INSTANCE))
        ).thenApply(properties ->
            properties
                .get("textures")
                .stream()
                .findFirst()
                .map(SkinKt::propertyToSkin)
                .orElse(new Skin("", Model.STEVE))
        );
    }

    @Override
    protected void applySkinToGame(Skin skin) {
        gameProfileManager.updatePlayerSkin(UUIDUtil.getClientUUID(), skin.getHash(), skin.getModel().getType());
    }
}
