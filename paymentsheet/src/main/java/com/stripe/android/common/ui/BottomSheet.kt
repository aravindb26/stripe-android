package com.stripe.android.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.ModalBottomSheetValue.Expanded
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterialApi::class)
internal class BottomSheetState(
    val modalBottomSheetState: ModalBottomSheetState,
    val keyboardHandler: BottomSheetKeyboardHandler,
) {

    private var dismissalType: DismissalType? = null

    suspend fun show() {
        repeatUntilSucceededOrLimit(10) {
            // Showing the bottom sheet can be interrupted.
            // We keep trying until it's fully displayed.
            modalBottomSheetState.show()
        }
    }

    suspend fun awaitDismissal(): DismissalType {
        snapshotFlow { modalBottomSheetState.isVisible }.first { isVisible -> !isVisible }
        return dismissalType ?: DismissalType.SwipedDownByUser
    }

    suspend fun hide() {
        dismissalType = DismissalType.Programmatically
        // We dismiss the keyboard before we dismiss the sheet. This looks cleaner and prevents
        // a CancellationException.
        keyboardHandler.dismiss()
        if (modalBottomSheetState.isVisible) {
            modalBottomSheetState.hide()
        }
    }

    internal enum class DismissalType {
        Programmatically,
        SwipedDownByUser,
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun rememberBottomSheetState(
    confirmValueChange: (ModalBottomSheetValue) -> Boolean = { true },
): BottomSheetState {
    val modalBottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmValueChange = confirmValueChange,
        skipHalfExpanded = true,
        animationSpec = tween(),
    )

    val keyboardHandler = rememberBottomSheetKeyboardHandler()

    return remember {
        BottomSheetState(
            modalBottomSheetState = modalBottomSheetState,
            keyboardHandler = keyboardHandler,
        )
    }
}

/**
 * Renders the provided [sheetContent] in a modal bottom sheet.
 *
 * @param state The [BottomSheetState] that controls the visibility of the bottom sheet.
 * @param onShow Called when the bottom sheet is displayed for the first time. This might be used to
 * navigate to a specific screen.
 * @param onDismissed Called when the user dismisses the bottom sheet by swiping down. You should
 * inform your view model about this change.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    onDismissed: () -> Unit,
    onShow: () -> Unit = {},
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    val systemUiController = rememberSystemUiController()
    val scrimColor = ModalBottomSheetDefaults.scrimColor

    val isExpanded = state.modalBottomSheetState.targetValue == Expanded

    val statusBarColorAlpha by animateFloatAsState(
        targetValue = if (isExpanded) scrimColor.alpha else 0f,
        animationSpec = tween(),
        label = "StatusBarColorAlpha",
    )

    var isFirstLaunch by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        state.show()
        if (isFirstLaunch) {
            isFirstLaunch = false
            onShow()
        }

        val dismissalType = state.awaitDismissal()
        if (dismissalType == BottomSheetState.DismissalType.SwipedDownByUser) {
            onDismissed()
        }
    }

    LaunchedEffect(systemUiController, statusBarColorAlpha) {
        systemUiController.setStatusBarColor(
            color = scrimColor.copy(statusBarColorAlpha),
            darkIcons = false,
        )
    }

    LaunchedEffect(systemUiController) {
        systemUiController.setNavigationBarColor(
            color = Color.Transparent,
            darkIcons = false,
        )
    }

    ModalBottomSheetLayout(
        modifier = modifier
            .statusBarsPadding()
            .imePadding(),
        sheetState = state.modalBottomSheetState,
        sheetContent = {
            sheetContent()
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        },
        content = {},
    )
}

private suspend fun repeatUntilSucceededOrLimit(
    limit: Int,
    block: suspend () -> Unit
) {
    var counter = 0
    while (counter < limit) {
        try {
            block()
            break
        } catch (ignored: CancellationException) {
            counter += 1
        }
    }
}