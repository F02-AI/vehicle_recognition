package com.example.vehiclerecognition.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.vehiclerecognition.data.models.Country

@Composable
fun CountrySelectionScreen(
    onCountrySelected: (Country) -> Unit
) {
    var selectedCountry by remember { mutableStateOf<Country?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
        
        // Country selection cards
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Country.values().forEach { country ->
                CountryCard(
                    country = country,
                    isSelected = selectedCountry == country,
                    onClick = { selectedCountry = country }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Continue button
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
        
        if (selectedCountry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = when (selectedCountry) {
                    Country.ISRAEL -> "Israeli format: NN-NNN-NN, NNN-NN-NNN"
                    Country.UK -> "UK format: LLNN-LLL (e.g., AB12-XYZ)"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CountryCard(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Flag
            val flagResourceId = context.resources.getIdentifier(
                country.flagResourceId,
                "drawable",
                context.packageName
            )
            if (flagResourceId != 0) {
                Image(
                    painter = painterResource(id = flagResourceId),
                    contentDescription = "${country.displayName} flag",
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // Country name and details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = country.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Text(
                    text = when (country) {
                        Country.ISRAEL -> "Hebrew license plates"
                        Country.UK -> "British license plates"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Selection indicator
            if (isSelected) {
                Icon(
                    painter = painterResource(android.R.drawable.checkbox_on_background),
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
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