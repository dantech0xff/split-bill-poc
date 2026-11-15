package com.dantech.splitvibe.ui.payments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvPrimaryFab
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.payments.PaymentMethod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaymentMethodsViewModel(private val repository: PaymentMethodRepository) : ViewModel() {
    /** Null until the list has been read. */
    val methods: StateFlow<List<SavedPaymentMethod>?> = repository.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: String?, method: PaymentMethod) {
        viewModelScope.launch { if (id == null) repository.add(method) else repository.replace(id, method) }
    }

    fun makeMain(id: String) {
        viewModelScope.launch { repository.makeMain(id) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }
}

/** The ways the user gets paid back, kept on this phone; the main one goes with requests unless another is picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    methods: List<SavedPaymentMethod>?,
    onSave: (id: String?, PaymentMethod) -> Unit,
    onMakeMain: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    // The method being changed, or NEW for one being added.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ways to get paid") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = { SvPrimaryFab(text = "Add", icon = Icons.Default.Add, onClick = { editing = NEW }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    "People you ask for money get a link, a QR code or the details of one of these, filled in with " +
                        "the amount where the payment app allows it. They are kept on this phone only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.md),
                )
            }
            if (methods?.isEmpty() == true) {
                item {
                    SvEmptyState(
                        icon = Icons.Default.Add,
                        text = "Add your bank account, PayPal, UPI, PromptPay or a payment link to put it on your requests.",
                    )
                }
            }
            itemsIndexed(methods.orEmpty(), key = { _, saved -> saved.id }) { index, saved ->
                MethodRow(
                    saved = saved,
                    position = SvRowPosition.of(index, methods.orEmpty().size),
                    onEdit = { editing = saved.id },
                    onMakeMain = { onMakeMain(saved.id) },
                    onRemove = { onRemove(saved.id) },
                )
            }
            item { Spacer(Modifier.height(FAB_CLEARANCE)) }
        }
    }

    editing?.let { id ->
        val saved = methods?.firstOrNull { it.id == id }
        PaymentMethodSheet(
            editing = saved,
            onSave = { method ->
                onSave(saved?.id, method)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MethodRow(saved: SavedPaymentMethod, position: SvRowPosition, onEdit: () -> Unit, onMakeMain: () -> Unit, onRemove: () -> Unit) {
    val method = saved.method
    val label = listOfNotNull(method.title, method.summary, "main".takeIf { saved.isMain }).joinToString(", ")
    SvListRow(position = position, label = label, onClickLabel = "Change", onClick = onEdit) {
        SvAvatar(method.title)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(method.title, style = MaterialTheme.typography.bodyLarge)
            Text(method.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (saved.isMain) {
            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        SvOverflowMenu(
            label = "More for ${method.title}",
            options = listOfNotNull(
                SvMenuOption("Use for requests", onMakeMain).takeUnless { saved.isMain },
                SvMenuOption("Remove", onRemove),
            ),
        )
    }
}

private const val NEW = "new"

/** Room below the last method, so the floating button never covers it. */
private val FAB_CLEARANCE = 88.dp
