package com.example.vehiclerecognition.ui.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.vehiclerecognition.model.VehicleColor
import com.example.vehiclerecognition.model.VehicleType
import com.example.vehiclerecognition.model.WatchlistEntry

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
                        viewModel.deleteWatchlistEntry(entry.licensePlate)
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
        items(entries, key = { it.licensePlate }) { entry ->
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
                Text(text = entry.licensePlate, style = MaterialTheme.typography.titleMedium)
                Text(text = "Type: ${entry.vehicleType.name}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Color: ${entry.vehicleColor.name}", style = MaterialTheme.typography.bodySmall)
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
                    label = { Text("License Plate (e.g., NN-NNN-NN)") },
                    isError = lpError != null,
                    singleLine = true
                )
                if (lpError != null) {
                    Text(lpError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    onItemSelected = { viewModel.onNewVehicleColorChange(it) }
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedItem.toString(),
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
                        text = { Text(item.toString()) },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete watchlist entry for LP: ${entry.licensePlate}?") },
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