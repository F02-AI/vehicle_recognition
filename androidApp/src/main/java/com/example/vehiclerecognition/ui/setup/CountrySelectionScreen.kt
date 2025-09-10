package com.example.vehiclerecognition.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vehiclerecognition.data.models.Country

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectionScreen(
    onCountrySelected: (Country) -> Unit
) {
    var selectedCountry by remember { mutableStateOf<Country?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Add some top spacing
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Welcome to Vehicle Recognition",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Please select your country to configure license plate format validation",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Country dropdown selection
        Text(
            text = "Select Country",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedCountry?.displayName ?: "Choose your country...",
                onValueChange = { },
                readOnly = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown arrow"
                    )
                },
                leadingIcon = selectedCountry?.let { country ->
                    {
                        CountryFlag(country = country, size = 24.dp)
                    }
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Country.values().sortedBy { it.displayName }.forEach { country ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CountryFlag(country = country, size = 24.dp)
                                Text(
                                    text = country.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        },
                        onClick = {
                            selectedCountry = country
                            expanded = false
                        }
                    )
                }
            }
        }
        
        // Flexible space to push button to bottom
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))
        
        // Continue button - always visible at bottom
        Button(
            onClick = { 
                selectedCountry?.let { onCountrySelected(it) }
            },
            enabled = selectedCountry != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.labelLarge
            )
        }
        
        // Bottom padding
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CountryFlag(
    country: Country,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    val context = LocalContext.current
    val flagResourceId = context.resources.getIdentifier(
        country.flagResourceId,
        "drawable",
        context.packageName
    )
    
    if (flagResourceId != 0) {
        Image(
            painter = painterResource(id = flagResourceId),
            contentDescription = "${country.displayName} flag",
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        // Fallback: Show country ISO code in a circular badge
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = country.isoCode,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (size.value * 0.4f).sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CountrySelectionScreenPreview() {
    MaterialTheme {
        CountrySelectionScreen(
            onCountrySelected = { }
        )
    }
}