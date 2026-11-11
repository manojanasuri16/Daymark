package com.daymark.app.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.Habit
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.Motion
import com.daymark.app.widget.StyleSwatch
import com.daymark.app.widget.WidgetKind
import com.daymark.app.widget.WidgetOffer
import com.daymark.app.widget.WidgetPreview
import com.daymark.app.widget.WidgetSnapshot
import com.daymark.app.widget.WidgetStyle
import kotlinx.coroutines.launch

/**
 * "Add to home screen" for one tracker: live previews of the widgets that suit it, a style row that restyles them,
 * and a primary button that starts the pin flow. [snapshot] is that tracker's real data.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddToHomeSheet(
    habit: Habit,
    snapshot: WidgetSnapshot?,
    onDismiss: () -> Unit,
    onPin: (WidgetKind, WidgetStyle) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val kinds = remember(habit.isBuild) { WidgetOffer.kindsFor(habit.isBuild) }
    var styleId by rememberSaveable { mutableStateOf(WidgetStyle.DEFAULT.id) }
    val style = WidgetStyle.of(styleId)
    val listState = rememberLazyListState()
    val selectedIndex by remember(kinds) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val half = (first?.size ?: 0) / 2
            val i = listState.firstVisibleItemIndex + if (listState.firstVisibleItemScrollOffset > half) 1 else 0
            i.coerceIn(0, kinds.lastIndex)
        }
    }
    val selectedKind = kinds[selectedIndex]
    LaunchedEffect(Unit) { haptics.confirm() }
    fun close(then: () -> Unit) { scope.launch { sheetState.hide() }.invokeOnCompletion { then() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp)) {
            Text(
                "Add to home screen", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "${habit.emoji}  ${habit.displayName}", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(18.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = maxWidth * 0.78f
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(kinds, key = { _, k -> k.name }) { i, kind ->
                        PreviewCard(
                            kind, style, snapshot, selected = i == selectedIndex,
                            modifier = Modifier.width(cardWidth),
                            onClick = {
                                if (i == selectedIndex) { haptics.confirm(); close { onPin(kind, style) } }
                                else { haptics.tick(); scope.launch { listState.animateScrollToItem(i) } }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                kinds.indices.forEach { i ->
                    val on = i == selectedIndex
                    val w by animateFloatAsState(if (on) 18f else 6f, Motion.standard(), label = "dotW")
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).width(w.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Style", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(WidgetStyle.entries) { _, s ->
                    val src = remember { MutableInteractionSource() }
                    Column(
                        Modifier.pressScale(src, 0.92f).clickable(src, null) { haptics.tick(); styleId = s.id },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StyleSwatch(s, s == style, MaterialTheme.colorScheme.primary)
                        Text(
                            s.label, style = MaterialTheme.typography.labelMedium,
                            color = if (s == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            val btnSrc = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale(btnSrc, 0.97f)
                    .clip(CircleShape)
                    .clickable(btnSrc, null) { haptics.confirm(); close { onPin(selectedKind, style) } },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        WidgetOffer.buttonLabel(selectedKind), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    kind: WidgetKind,
    style: WidgetStyle,
    snapshot: WidgetSnapshot?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(if (selected) 1f else 0.6f, Motion.standard(), label = "cardAlpha")
    val scale by animateFloatAsState(if (selected) 1f else 0.95f, Motion.standard(), label = "cardScale")
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        Motion.standard(), label = "cardBorder",
    )
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(alpha)
            .pressScale(src, 0.97f)
            .clip(RoundedCornerShape(26.dp))
            .clickable(src, null, onClick = onClick)
            .semantics { contentDescription = "${kind.title} widget. ${kind.blurb}" },
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(168.dp), contentAlignment = Alignment.Center) {
                WidgetPreview(
                    kind, style, snapshot?.takeIf { it.onboarded },
                    when (kind) {
                        WidgetKind.RING -> Modifier.size(150.dp)
                        WidgetKind.STATS -> Modifier.fillMaxWidth(0.82f)
                        else -> Modifier.fillMaxWidth()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(kind.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                kind.blurb, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 2, minLines = 2,
            )
        }
    }
}
