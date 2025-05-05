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
package gg.essential.gui.common

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.gui.common.StyledButton.Style
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.elementa.state.v2.toV2
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedWidth
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.effect
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.util.hoverScope
import gg.essential.vigilance.utils.onLeftClick

/**
 * An immutable outline button.
 * To build one of these, see [outlineButton].
 */
class OutlineButton(
    /** The style for this button. */
    private val style: State<Style>,

    /** Whether this button is disabled or not. When true, click events will not be propagated. */
    private val disabled: State<Boolean>,

    private val content: LayoutScope.(style: State<MenuButton.Style>) -> Unit,
) : UIBlock() {

    val enabled: Boolean
        get() = !disabled.getUntracked()

    @Deprecated("Workaround for legacy EssentialModal, don't use")
    val forceHoverStyle = mutableStateOf(false)

    private val hovered = hoverScope().toV2()

    private val currentStyle = memo {
        val style = style()
        when {
            disabled() -> style.disabledStyle
            forceHoverStyle() || hovered() -> style.hoveredStyle
            else -> style.defaultStyle
        }
    }

    init {
        layoutAsBox(
            Modifier.childBasedWidth(5f).height(19f).color(memo { currentStyle().buttonColor })
                .effect {
                    OutlineEffect(memo { currentStyle().outlineColor }.toV1(this), stateOf(1f).toV1(this), drawInsideChildren = true)
                }.hoverScope()
        ) {
            box(Modifier.alignVertical(Alignment.Center(true))) {
                content(currentStyle)
            }
        }
        onLeftClick { event ->
            if (disabled.getUntracked()) {
                event.stopImmediatePropagation()
            }
        }
    }
}

