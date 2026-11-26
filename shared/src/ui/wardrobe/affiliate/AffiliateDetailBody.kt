package nl.petrichor.app.ui.wardrobe.affiliate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.ui.wardrobe.DetailRow

/** The "Details" rows (store, price, type), the description and the shop button. */
@Composable
internal fun AffiliateDetailBody(
    item: AffiliateItem,
    onShop: (AffiliateItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Details",
            style = MaterialTheme.typography.titleMedium,
        )
        DetailRow(
            icon = MaterialSymbols.STOREFRONT,
            label = "Store",
            value = item.store.displayName,
        )
        item.price?.let { price ->
            DetailRow(
                icon = MaterialSymbols.PAYMENTS,
                label = "Price",
                value = price,
            )
        }
        DetailRow(
            icon = MaterialSymbols.CATEGORY,
            label = "Type",
            value = item.typeName,
        )

        item.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { onShop(item) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Shop at ${item.store.displayName}")
        }
    }
}
