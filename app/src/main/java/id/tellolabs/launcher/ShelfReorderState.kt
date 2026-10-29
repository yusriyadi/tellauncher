package id.tellolabs.launcher

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlin.math.abs

/** Keeps drag previews in memory and saves only the final order on drop. */
@Stable
internal class ShelfReorderState(
    private val original: List<Shelf>,
    private val listState: LazyListState,
) {
    var shelves by mutableStateOf(original)
        private set
    var draggedId by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    private var top by mutableFloatStateOf(0f)
    private var height = 0

    private fun item(id: String) = listState.layoutInfo.visibleItemsInfo
        .find { it.key == "shelf_$id" }

    fun start(id: String) {
        if (saving) return
        val item = item(id) ?: return
        draggedId = id
        top = item.offset.toFloat()
        height = item.size
    }

    fun drag(delta: Float) {
        if (draggedId == null) return
        top += delta
        moveOverTarget()
    }

    private fun moveOverTarget() {
        val id = draggedId ?: return
        val center = top + height / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != "shelf_$id" && center >= it.offset && center < it.offset + it.size &&
                shelves.any { shelf -> it.key == "shelf_${shelf.id}" }
        } ?: return
        val from = shelves.indexOfFirst { it.id == id }
        val to = shelves.indexOfFirst { "shelf_${it.id}" == target.key }
        if (from < 0 || to < 0) return
        // Keep the viewport stationary even when moving its first visible item.
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        shelves = shelves.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun translation(id: String): Float =
        if (draggedId == id) top - (item(id)?.offset ?: top.toInt()) else 0f

    suspend fun autoScroll(edge: Float, maxStep: Float) {
        val info = listState.layoutInfo
        val center = top + height / 2f
        val amount = when {
            center < info.viewportStartOffset + edge ->
                -((info.viewportStartOffset + edge - center) / edge).coerceIn(0f, 1f) * maxStep
            center > info.viewportEndOffset - edge ->
                ((center - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f) * maxStep
            else -> 0f
        }
        if (abs(amount) > 0f && listState.scrollBy(amount) != 0f) moveOverTarget()
    }

    fun cancel() {
        draggedId = null
        shelves = original
    }

    suspend fun drop(save: suspend (List<String>) -> Boolean) {
        if (draggedId == null) return
        draggedId = null
        if (shelves == original) return
        saving = true
        try {
            if (!save(shelves.map { it.id })) shelves = original
        } finally {
            saving = false
        }
    }
}

@Composable
internal fun rememberShelfReorderState(
    shelves: List<Shelf>,
    listState: LazyListState,
): ShelfReorderState {
    val state = remember(shelves, listState) { ShelfReorderState(shelves, listState) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(state, state.draggedId) {
        if (state.draggedId != null) {
            while (true) {
                withFrameNanos { }
                state.autoScroll(edge = 48f * density, maxStep = 8f * density)
            }
        }
    }
    return state
}

internal fun Modifier.shelfDragOffset(state: ShelfReorderState, id: String): Modifier =
    zIndex(if (state.draggedId == id) 1f else 0f).graphicsLayer {
        translationY = state.translation(id)
        shadowElevation = if (state.draggedId == id) 4f * density else 0f
    }
