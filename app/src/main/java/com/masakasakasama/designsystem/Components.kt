package com.masakasakasama.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.masakasakasama.designsystem.generated.TatsuDimens
import com.masakasakasama.designsystem.generated.TatsuType

@Composable
fun TatsuCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalTatsuColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TatsuDimens.CardRadius),
        color = if (elevated) colors.surfaceElevated else colors.surface,
        contentColor = colors.textPrimary,
        border = BorderStroke(TatsuDimens.CardBorderWidth, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(TatsuDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(TatsuDimens.CardGap),
            content = content,
        )
    }
}

@Composable
fun TatsuMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTatsuColors.current
    Column(modifier = modifier) {
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight(TatsuType.MetricWeight),
            color = colors.textPrimary,
        )
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
    }
}

@Composable
fun TatsuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalTatsuColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TatsuDimens.ButtonHeight),
        shape = RoundedCornerShape(TatsuDimens.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
        ),
        contentPadding = PaddingValues(horizontal = TatsuDimens.Lg),
    ) {
        Text(text)
    }
}
