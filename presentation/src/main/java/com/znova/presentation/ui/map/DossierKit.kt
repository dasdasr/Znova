package com.znova.presentation.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pack-specific dossier components.
 * Replace this file and the theme when migrating to another design system.
 */
internal val LocalTheme = staticCompositionLocalOf { PackTheme() }

internal val theme: PackTheme
    @Composable get() = LocalTheme.current

@Composable
internal fun DragHandle(
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable(onClickLabel = "Resize", onClick = onToggle)
            .semantics { contentDescription = "Resize panel" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(theme.ink.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
        )
    }
}

// -> FeldIconButton
@Composable
internal fun CloseButton(onClose: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClickLabel = "Close", role = Role.Button, onClick = onClose)
            .semantics { contentDescription = "Close" },
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = theme.ink, fontSize = 18.sp)
    }
}

// -> FeldSegmentedControl
@Composable
internal fun DossierTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val skin = theme
    val shape = RoundedCornerShape(skin.tabCorner)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(skin.card)
            .border(1.dp, skin.ink, shape),
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                    .padding(4.dp)
                    .background(if (active) skin.ink else Color.Transparent, shape),
                contentAlignment = Alignment.Center,
            ) {
                Label(title, if (active) skin.paper else skin.ink)
            }
        }
    }
}

// -> FeldCard
@Composable
internal fun DossierCard(
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(theme.cardCorner)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.card)
            .border(1.dp, theme.ink.copy(alpha = 0.25f), shape)
            .then(modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        content = body,
    )
}

// -> FeldDivider
@Composable
internal fun DossierDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(theme.ink.copy(alpha = 0.15f)))
}

// -> FeldSectionHeader
@Composable
internal fun Section(
    title: String,
    body: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title, theme.accent)
        body()
    }
}

@Composable
internal fun VerdictRow(
    title: String,
    text: String,
    color: Color,
) {
    Row(
        Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Label(title, color, Modifier.width(80.dp))
        Body(text, theme.ink, Modifier.weight(1f))
    }
}

// -> FeldChip not clickable
@Composable
internal fun DossierChip(
    text: String,
    color: Color,
) {
    val shape = RoundedCornerShape(theme.chipCorner)
    Body(
        text,
        color,
        Modifier
            .background(theme.card, shape)
            .border(1.dp, color, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        size = 13.sp,
    )
}

@Composable
internal fun Label(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = theme.labelFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
    )
}

@Composable
internal fun Body(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    italic: Boolean = false,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontFamily = theme.bodyFont,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = size,
        lineHeight = size * 1.3f,
    )
}
