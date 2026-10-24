package tr.sakin.chat.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable fun RightMenu(settings: () -> Unit, games: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "Sağ menü") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Oyunlar") }, leadingIcon = { Icon(Icons.Outlined.SportsEsports,null) }, onClick = { open = false; games() })
        DropdownMenuItem(text = { Text("Ayarlar") }, leadingIcon = { Icon(Icons.Outlined.Settings,null) }, onClick = { open = false; settings() })
    }
}
/** Preserve reading position. History prepends and delivery receipts never force a scroll. */
@Composable fun rememberSmoothMessageScroll(list: LazyListState, newestId: String?, count: Int, newestMine: Boolean): Boolean {
    val nearBottom by remember(list) { derivedStateOf {
        val info = list.layoutInfo
        info.totalItemsCount == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 2
    } }
    var previous by remember(list) { mutableStateOf<String?>(null) }
    LaunchedEffect(newestId) {
        if(newestId != null && count > 0) {
            if(previous == null) list.scrollToItem(count - 1)
            else if(nearBottom || newestMine) list.animateScrollToItem(count - 1)
            previous = newestId
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.viewportEndOffset }.distinctUntilChanged().collect {
            if(nearBottom && list.layoutInfo.totalItemsCount > 0) list.scrollToItem(list.layoutInfo.totalItemsCount - 1)
        }
    }
    return nearBottom
}
