package com.organizer.presentation.screens.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.presentation.OrganizerViewModel

@Composable
fun CategoryHeader(
    categoryPath: MutableList<CategoryEntity>,
    onBackClick: () -> Unit,
    onCategoryClick: (CategoryEntity) -> Unit,
    viewModel: OrganizerViewModel,
    modifier: Modifier = Modifier,
) {
    val sport = categoryPath.firstOrNull()
    val subcategories = categoryPath.drop(1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Icon for back click
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Selected sport type
            sport?.let {
                // Same media-tick pattern as the list items: the icon appears
                // once its download completes.
                val mediaTick by viewModel.mediaVersion.collectAsState()
                AsyncImage(
                    model = remember(mediaTick) { viewModel.getIconFile(it.iconUrl ?: "") },
                    contentDescription = it.name,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = it.name,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }

        // Breadcrumb path below the static sport header. The sport is always
        // shown as the first item; every item except the current one is
        // clickable and navigates directly to that level.
        sport?.let { currentSport ->
            val breadcrumbItems = listOf(currentSport) + subcategories

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.padding(start = 56.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                breadcrumbItems.forEachIndexed { index, category ->
                    val isCurrent = index == breadcrumbItems.lastIndex
                    val blockColor = if (isCurrent) Color(0xFF6750A4) else Color(0xFFF1ECF2)
                    val textColor = if (isCurrent) Color.White else Color(0xFF6750A4)

                    Box(
                        modifier = (if (isCurrent) {
                            Modifier
                        } else {
                            Modifier.clickable { onCategoryClick(category) }
                        })
                            .align(Alignment.CenterVertically)
                            .background(blockColor, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = category.name,
                            fontSize = 16.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                        )
                    }

                    if (index < breadcrumbItems.lastIndex) {
                        Text(
                            text = ">",
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(horizontal = 6.dp),
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
