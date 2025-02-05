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
package gg.essential.gui.wardrobe.modals

import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.state.BasicState
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.modal.ConfirmDenyModal
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.FloatPosition
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillRemainingWidth
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.image
import gg.essential.gui.layoutdsl.layout
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.wardrobe.Item
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.network.connectionmanager.coins.CoinsManager
import java.awt.Color

class PurchaseConfirmationModal(
    modalManager: ModalManager,
    private val items: List<Pair<Item.CosmeticOrEmote, State<Int>>>,
    private val totalAmount: State<Int>,
    primaryAction: () -> Unit,
) : ConfirmDenyModal(modalManager, requiresButtonPress = false) {
    private val discountAmount = memo { items.sumOf { it.second() } - totalAmount() }

    init {
        titleText = "Confirm your purchase!"
        titleTextColor = EssentialPalette.TEXT
        primaryButtonText = "Purchase"

        contentTextSpacingState.rebind(BasicState(0f))
        spacer.setHeight(17.pixels)

        customContent.constrain {
            y = SiblingConstraint(14f)
        }

        customContent.layout {
            purchaseSummary()
        }

        onPrimaryAction(primaryAction)
    }

    private fun LayoutScope.purchaseSummary() {
        box(
            Modifier
                .fillWidth(rightPadding = 1f)
                .childBasedHeight(padding = 15f)
                .color(EssentialPalette.PURCHASE_CONFIRMATION_MODAL_SECONDARY)
                .shadow()
        ) {
            column(Modifier.fillWidth(padding = 16f), Arrangement.spacedBy(7f)) {
                // If there is only one item in the cart, and there is no discount, we don't need to show a total.
                if_({ items.size == 1 && discountAmount() == 0 }) {
                    itemEntry(items[0], Modifier.color(EssentialPalette.TEXT_HIGHLIGHT))
                } `else` {
                    column(Modifier.fillWidth(), Arrangement.spacedBy(4f)) {
                        items.forEach { itemEntry(it) }
                    }

                    box(Modifier.fillWidth().height(1f).color(EssentialPalette.BUTTON))

                    totalAndDiscount()
                }
            }
        }
    }

    private fun LayoutScope.totalAndDiscount() {
        column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
            if_({ discountAmount() != 0 }) {
                listEntry(
                    "Discount",
                    discountAmount,
                    nameModifier = Modifier.color(EssentialPalette.GREEN),
                    costModifier = Modifier.color(EssentialPalette.TEXT_HIGHLIGHT),
                )
            }

            listEntry("Total", totalAmount, Modifier.color(EssentialPalette.TEXT_HIGHLIGHT))
        }
    }

    private fun LayoutScope.itemEntry(
        itemAndPrice: Pair<Item.CosmeticOrEmote, State<Int>>,
        nameModifier: Modifier = Modifier,
        costModifier: Modifier = nameModifier,
    ) {
        val (item, price) = itemAndPrice
        listEntry(item.name, price, nameModifier, costModifier)
    }

    private fun LayoutScope.listEntry(
        name: String,
        cost: State<Int>,
        nameModifier: Modifier = Modifier,
        costModifier: Modifier = nameModifier,
    ) {
        val defaultTextModifier = Modifier.color(EssentialPalette.TEXT_MID_GRAY).shadow(Color.BLACK)

        row(Modifier.fillWidth(), Arrangement.spacedBy(spacing = 15f)) {
            row(Modifier.fillRemainingWidth(), Arrangement.spacedBy(float = FloatPosition.START)) {
                text(
                    name,
                    Modifier.then(defaultTextModifier).then(nameModifier),
                    truncateIfTooSmall = true,
                )
            }

            bind(cost) { costAmount ->
                wrappedText(
                    "${CoinsManager.COIN_FORMAT.format(costAmount)}{coin-icon}",
                    textModifier = defaultTextModifier.then(costModifier),
                ) {
                    "coin-icon" {
                        row {
                            spacer(width = 2f)
                            image(EssentialPalette.COIN_7X, Modifier.shadow(Color.BLACK))
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun forEquippedItemsPurchasable(modalManager: ModalManager, state: WardrobeState, primaryAction: () -> Unit): PurchaseConfirmationModal {
            val itemsAndPriceInfo = state.equippedCosmeticsPurchasable.getUntracked().map { it to it.getPricingInfo(state) }
            val totalCost = memo { itemsAndPriceInfo.sumOf { (_, price) -> price()?.realCost ?: 0 } }

            return PurchaseConfirmationModal(
                modalManager,
                itemsAndPriceInfo.map { (item, price) -> item to price.map { it?.baseCost ?: 0 } },
                totalCost,
                primaryAction,
            )
        }

        fun forItem(
            modalManager: ModalManager,
            item: Item.CosmeticOrEmote,
            state: WardrobeState,
            primaryAction: () -> Unit
        ): PurchaseConfirmationModal {
            val priceInfo = item.getPricingInfo(state)

            return PurchaseConfirmationModal(
                modalManager,
                listOf(item to priceInfo.map { it?.baseCost ?: 0 }),
                priceInfo.map { it?.realCost ?: 0 },
                primaryAction,
            )
        }

        fun forBundle(modalManager: ModalManager, bundle: Item.Bundle, state: WardrobeState, primaryAction: () -> Unit): PurchaseConfirmationModal {
            val bundleInfo = bundle.getPricingInfo(state)

            val allCosmetics = state.rawCosmetics.getUntracked()
            val unlockedCosmetics = state.unlockedCosmetics.getUntracked()

            val itemsToPurchase = bundle.cosmetics.values
                .filter { it !in unlockedCosmetics }
                .mapNotNull { cosmeticId ->
                    allCosmetics.find { it.id == cosmeticId }?.let { Item.CosmeticOrEmote(it) }
                }

            val itemsAndPriceInfo = itemsToPurchase.map { item ->
                val price = item.getPricingInfoInternal(listOf())
                item to stateOf(price?.baseCost ?: 0)
            }

            return PurchaseConfirmationModal(
                modalManager,
                itemsAndPriceInfo,
                bundleInfo.map { it?.realCost ?: 0 },
                primaryAction,
            )
        }
    }
}