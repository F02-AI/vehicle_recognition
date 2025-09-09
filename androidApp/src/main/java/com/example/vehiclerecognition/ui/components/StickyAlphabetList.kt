package com.example.vehiclerecognition.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class for items in the sticky alphabet list
 */
data class AlphabetListItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val content: @Composable () -> Unit
)

/**
 * Sticky alphabet list with section headers that stick when scrolling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickyAlphabetList(
    items: List<AlphabetListItem>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    // Group items by first letter
    val groupedItems = remember(items) {
        items
            .groupBy { it.title.first().uppercase() }
            .toSortedMap()
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groupedItems.forEach { (letter, itemsInSection) ->
            stickyHeader(key = "header_$letter") {
                AlphabetSectionHeader(
                    letter = letter,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            items(
                items = itemsInSection,
                key = { item -> item.id }
            ) { item ->
                AlphabetListItemContent(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Section header for each alphabet letter
 */
@Composable
private fun AlphabetSectionHeader(
    letter: String,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        label = "header_alpha"
    )
    
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .alpha(alpha)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        
        Divider(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}

/**
 * Individual item content in the list
 */
@Composable
private fun AlphabetListItemContent(
    item: AlphabetListItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            item.content()
        }
    }
}

/**
 * Extension function to get section headers for external use
 */
@Composable
fun rememberAlphabetSections(items: List<AlphabetListItem>): List<String> {
    return remember(items) {
        items
            .map { it.title.first().uppercase() }
            .distinct()
            .sorted()
    }
}

/**
 * Alphabet index sidebar for quick navigation
 */
@Composable
fun AlphabetIndex(
    sections: List<String>,
    onSectionClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                MaterialTheme.shapes.medium
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        sections.forEach { section ->
            Text(
                text = section,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(2.dp)
            )
        }
    }
}