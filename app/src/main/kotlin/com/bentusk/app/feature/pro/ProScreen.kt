package com.bentusk.app.feature.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bentusk.app.R
import com.bentusk.app.core.billing.PurchaseEvent
import com.bentusk.app.ui.theme.BentuskColors
import com.bentusk.engine.Entitlement
import com.bentusk.engine.Plans

@Composable
fun ProScreen(onClose: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val pendingText = stringResource(R.string.purchase_pending)
    val failedText = stringResource(R.string.purchase_failed)
    val unavailableText = stringResource(R.string.purchase_unavailable)

    LaunchedEffect(event) {
        val message = when (event) {
            PurchaseEvent.Pending -> pendingText
            is PurchaseEvent.Failed -> failedText
            else -> null
        }
        if (event != null) viewModel.consumeEvent()
        if (message != null) snackbar.showSnackbar(message)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
            }
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(BentuskColors.Gold, BentuskColors.GoldDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.WorkspacePremium, null, Modifier.size(48.dp), tint = BentuskColors.Ink)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                statusLine(entitlement),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(24.dp))

            Feature(Icons.Rounded.Block, R.string.pro_feature_ads)
            Feature(Icons.Rounded.ViewCarousel, R.string.pro_feature_slides)
            Feature(Icons.Rounded.HighQuality, R.string.pro_feature_png)
            Feature(Icons.Rounded.BorderStyle, R.string.pro_feature_frames)
            Feature(Icons.Rounded.Gradient, R.string.pro_feature_covers)
            Feature(Icons.Rounded.Layers, R.string.pro_feature_plans)

            Spacer(Modifier.height(24.dp))
            if (entitlement == Entitlement.Pro) {
                Text(
                    stringResource(R.string.pro_owned),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity == null || !viewModel.buy(activity)) {
                            // Play has not returned the product yet (offline, or not published to this account).
                            viewModel.restore()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        price?.let { stringResource(R.string.pro_buy_price, it) }
                            ?: stringResource(R.string.pro_buy, stringResource(R.string.pro_default_price)),
                    )
                }
                if (price == null) {
                    Text(
                        unavailableText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    stringResource(R.string.pro_once),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = viewModel::restore) { Text(stringResource(R.string.pro_restore)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun statusLine(entitlement: Entitlement): String = when (entitlement) {
    is Entitlement.Trial -> stringResource(R.string.trial_days_left, entitlement.daysLeft, Plans.TRIAL_DAYS)
    Entitlement.Free -> stringResource(R.string.trial_ended)
    Entitlement.Pro -> stringResource(R.string.pro_thanks)
}

@Composable
private fun Feature(icon: ImageVector, text: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
