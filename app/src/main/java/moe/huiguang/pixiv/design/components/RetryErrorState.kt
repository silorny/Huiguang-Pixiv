package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.design.AdaptiveTokens

fun isRateLimitError(message: String?): Boolean =
    message?.contains("429") == true

/**
 * Full-screen or inline retry/rate-limit error block with clear explanation text
 * and a COUI 凝光 primary retry button.
 */
@Composable
fun RetryErrorState(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop? = null,
    fillScreen: Boolean = true,
    fallbackMessage: String = stringResource(R.string.error_load_failed),
    extraAction: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = CouiPageDefaults.themeColors()
    val rateLimited = isRateLimitError(error)
    val headline = if (rateLimited) {
        stringResource(R.string.error_rate_limit_title)
    } else {
        error?.takeIf { it.isNotBlank() } ?: fallbackMessage
    }
    val explanation = stringResource(
        if (rateLimited) R.string.error_rate_limit_summary else R.string.error_general_paused_summary,
    )
    Box(
        modifier = if (fillScreen) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AdaptiveTokens.ReadableMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = if (fillScreen) 24.dp else 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = headline,
                kind = CouiTextKind.ItemTitle,
                color = colors.title,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = explanation,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                backdrop = backdrop,
            )
            if (extraAction != null) {
                extraAction()
            }
        }
    }
}
