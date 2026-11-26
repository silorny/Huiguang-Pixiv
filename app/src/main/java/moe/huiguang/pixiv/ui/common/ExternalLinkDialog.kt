package moe.huiguang.pixiv.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R

private val ExternalUrlCardShape = CouiShape(14.dp, 3f)

@Composable
fun ExternalLinkConfirmDialog(
    visible: Boolean,
    url: String,
    dark: Boolean,
    colors: CouiPageColors,
    material: CouiDialogMaterial?,
    backdrop: CouiBackdrop?,
    onDismiss: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenUrl: () -> Unit,
) {
    val borderColor = if (dark) Color(0x24FFFFFF) else Color(0x16000000)
    val urlSurface = if (dark) Color(0x18FFFFFF) else Color(0x0B000000)
    Dialog(
        visible = visible,
        onDismissRequest = onDismiss,
        dark = dark,
        title = stringResource(R.string.author_external_link_title),
        message = stringResource(R.string.author_external_link_message),
        placement = CouiDialogPlacement.Bottom,
        material = material,
        backdrop = backdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.author_external_link_open),
            onClick = onOpenUrl,
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiDialogDefaults.contentPadding)
                .padding(bottom = 16.dp)
                .clip(ExternalUrlCardShape)
                .background(urlSurface, ExternalUrlCardShape)
                .border(0.5.dp, borderColor, ExternalUrlCardShape)
                .clickable(onClick = onCopyUrl)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                text = url,
                style = TextStyle(
                    color = colors.title,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = stringResource(R.string.about_github_copy),
                style = TextStyle(
                    color = CouiTheme.colorScheme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
        }
    }
}

internal fun copyPlainText(
    context: Context,
    label: String,
    text: String,
    toastMessage: String,
) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

internal fun openExternalUrl(context: Context, url: String) {
    val parsed = runCatching { Uri.parse(url.trim()) }.getOrNull()
    val scheme = parsed?.scheme?.lowercase()
    if (parsed == null || (scheme != "http" && scheme != "https")) {
        Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, parsed))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
    }
}
