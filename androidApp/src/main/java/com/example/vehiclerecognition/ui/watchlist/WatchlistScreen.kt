package com.example.vehiclerecognition.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry

// Extension functions to format enum values with proper case
fun VehicleType.toFormattedString(): String {
    return name.lowercase().replaceFirstChar { it.uppercase() }
}

fun VehicleColor.toFormattedString(): String {
    return name.lowercase().replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel // Injected
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDialog by viewModel.showAddDialog.collectAsState()
    var entryToDelete by remember { mutableStateOf<WatchlistEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Watchlist") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddDialog() }) {
                Icon(Icons.Filled.Add, "Add to watchlist")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = uiState) {
                is WatchlistUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is WatchlistUiState.Success -> {
                    if (state.entries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Watchlist is empty. Tap '+' to add an entry.")
                        }
                    } else {
                        WatchlistContent(entries = state.entries) { entry ->
                            entryToDelete = entry // Show confirmation dialog
                        }
                    }
                }
                is WatchlistUiState.Error -> {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error loading watchlist: ${state.message}")
                    }
                }
            }
            if (showDialog) {
                AddWatchlistEntryDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.dismissAddDialog(false) },
                    onConfirm = { viewModel.addWatchlistEntry() }
                )
            }

            entryToDelete?.let { entry ->
                DeleteConfirmationDialog(
                    entry = entry,
                    onConfirmDelete = {
                        val licensePlate = entry.licensePlate
                        if (licensePlate != null) {
                            viewModel.deleteWatchlistEntry(licensePlate)
                        } else {
                            // For entries without license plate, we need to delete by the combination of color and type
                            viewModel.deleteEntryByColorAndType(entry.vehicleColor, entry.vehicleType)
                        }
                        entryToDelete = null
                    },
                    onDismiss = {
                        entryToDelete = null
                    }
                )
            }
        }
    }
}

@Composable
fun WatchlistContent(
    entries: List<WatchlistEntry>,
    onDeleteClicked: (WatchlistEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = entries,
            // Use a combination of fields as a unique key
            key = { entry -> 
                if (entry.licensePlate != null) {
                    "lp:${entry.licensePlate}"
                } else {
                    "color:${entry.vehicleColor.name}:type:${entry.vehicleType.name}:${System.identityHashCode(entry)}"
                }
            }
        ) { entry ->
            WatchlistItem(entry = entry, onDeleteClicked = { onDeleteClicked(entry) })
        }
    }
}

@Composable
fun WatchlistItem(
    entry: WatchlistEntry,
    onDeleteClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val licensePlate = entry.licensePlate
                if (licensePlate != null) {
                    Text(text = licensePlate, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(text = "(No license plate)", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                }
                Text(text = "Type: ${entry.vehicleType.toFormattedString()}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Color: ${entry.vehicleColor.toFormattedString()}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(4.dp))
                    ColorIcon(vehicleColor = entry.vehicleColor)
                }
            }
            IconButton(onClick = onDeleteClicked) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
            }
        }
    }
}

@Composable
fun AddWatchlistEntryDialog(
    viewModel: WatchlistViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val newLp by viewModel.newLicensePlate.collectAsState()
    val newType by viewModel.newVehicleType.collectAsState()
    val newColor by viewModel.newVehicleColor.collectAsState()
    val lpError by viewModel.lpValidationError.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Add New Watchlist Entry", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = newLp,
                    onValueChange = { viewModel.onNewLicensePlateChange(it) },
                    label = { Text("License Plate (Optional for Color/Type)") },
                    isError = lpError != null,
                    singleLine = true
                )
                if (lpError != null) {
                    Text(lpError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Note: License plate is required for LP-based detection modes.", 
                         style = MaterialTheme.typography.bodySmall)
                }

                // Dropdowns for Type and Color
                ExposedDropdownMenuBox(
                    label = "Vehicle Type",
                    items = VehicleType.values().toList(),
                    selectedItem = newType,
                    onItemSelected = { viewModel.onNewVehicleTypeChange(it) }
                )
                ExposedDropdownMenuBox(
                    label = "Vehicle Color",
                    items = VehicleColor.values().toList(),
                    selectedItem = newColor,
                    onItemSelected = { viewModel.onNewVehicleColorChange(it) },
                    itemContent = { colorItem ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorIcon(vehicleColor = colorItem)
                            Spacer(Modifier.width(8.dp))
                            Text(colorItem.toFormattedString())
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm) { Text("Add") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExposedDropdownMenuBox(
    label: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit = { item -> 
        when (item) {
            is VehicleType -> Text(item.toFormattedString())
            is VehicleColor -> Text(item.toFormattedString())
            else -> Text(item.toString())
        }
    }
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = when (selectedItem) {
                    is VehicleType -> selectedItem.toFormattedString()
                    is VehicleColor -> selectedItem.toFormattedString()
                    else -> selectedItem.toString()
                },
                onValueChange = { /* Read-only */ },
                label = { Text(label) },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { itemContent(item) },
                        onClick = {
                            onItemSelected(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WatchlistContentPreview() {
    val entries = listOf(
        WatchlistEntry("12-345-67", VehicleType.CAR, VehicleColor.BLUE),
        WatchlistEntry("123-45-678", VehicleType.TRUCK, VehicleColor.RED)
    )
    MaterialTheme {
        WatchlistContent(entries = entries, onDeleteClicked = {})
    }
}

// Add a Preview for the Dialog if needed, requires a WatchlistViewModel instance.
// @Preview(showBackground = true)
// @Composable
// fun AddWatchlistEntryDialogPreview() {
//     // This preview is complex because it needs a ViewModel.
//     // Consider creating a fake ViewModel or passing lambdas directly for UI previews.
//     MaterialTheme {
        // AddWatchlistEntryDialog( ... )
//     }
// }

@Composable
fun DeleteConfirmationDialog(
    entry: WatchlistEntry,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val licensePlate = entry.licensePlate
    val displayText = if (licensePlate != null) {
        "Are you sure you want to delete watchlist entry for LP: $licensePlate?"
    } else {
        "Are you sure you want to delete this ${entry.vehicleColor.toFormattedString()} ${entry.vehicleType.toFormattedString()} watchlist entry?"
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text(displayText) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete()
                }
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper to get Compose Color
fun VehicleColor.toComposeColor(): Color {
    return when (this) {
        VehicleColor.RED -> Color.Red
        VehicleColor.BLUE -> Color.Blue
        VehicleColor.GREEN -> Color.Green
        VehicleColor.WHITE -> Color.White
        VehicleColor.BLACK -> Color.Black
        VehicleColor.GRAY -> Color.Gray
        VehicleColor.YELLOW -> Color.Yellow
        // Consider adding an 'else' branch for future-proofing if more colors are added
        // else -> Color.Transparent // Or some default
    }
}

@Composable
fun ColorIcon(vehicleColor: VehicleColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .background(vehicleColor.toComposeColor(), CircleShape)
            .border(1.dp, Color.DarkGray, CircleShape)
    )
} 