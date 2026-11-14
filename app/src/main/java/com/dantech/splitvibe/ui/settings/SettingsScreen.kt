package com.dantech.splitvibe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency

/** The app's own settings: the currency it works in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onSave: (localCurrency: String) -> Unit, onBack: () -> Unit) {
    var localCurrency by rememberSaveable { mutableStateOf(settings.localCurrency) }
    val currencyIsValid = BillCurrency.isKnownCode(localCurrency)

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(SvSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.lg),
        ) {
            OutlinedTextField(
                value = localCurrency,
                onValueChange = { localCurrency = it.uppercase().take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Local currency") },
                singleLine = true,
                isError = !currencyIsValid,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                supportingText = {
                    Text(
                        if (currencyIsValid) {
                            "The currency this app works in, for example VND. Used when a bill does not show its currency."
                        } else {
                            "Enter a three-letter currency code such as VND, USD or EUR."
                        },
                    )
                },
            )

            SvPrimaryButton("Save", onClick = { onSave(localCurrency) }, enabled = currencyIsValid)
        }
    }
}
