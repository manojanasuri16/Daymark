package com.daymark.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 1.5 -> "1.5", 2.0 -> "2", 250.0 -> "250". */
fun formatAmount(v: Double): String =
    if (kotlin.math.abs(v - Math.rint(v)) < 1e-9) Math.rint(v).toLong().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')

fun amountWithUnit(v: Double, unit: String): String =
    if (unit.isBlank()) formatAmount(v) else "${formatAmount(v)} ${unit.trim()}"

/** A small dialog to type an exact amount for a day. 0 clears the day. */
@Composable
fun AmountDialog(
    title: String,
    unit: String,
    initial: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(if (initial > 0) formatAmount(initial) else "") }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
    val valid = text.isBlank() || (parsed != null && parsed >= 0 && parsed < 1_000_000)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(10) },
                singleLine = true,
                isError = !valid,
                label = { Text(if (unit.isBlank()) "Amount" else "Amount ($unit)") },
                supportingText = { Text("Leave empty to clear the day.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(if (text.isBlank()) 0.0 else parsed ?: 0.0) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
