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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Icon for back click
            IconButton(
                onClick = onBackClick
            ) {
                Text(
                    text = "←",
                    fontSize = 28.sp
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
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.Start
            ) {
                breadcrumbItems.forEachIndexed { index, category ->
                    val isCurrent = index == breadcrumbItems.lastIndex
                    Text(
                        text = category.name,
                        fontSize = 18.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) Color.Black else Color(0xFF4F378A),
                        modifier = if (isCurrent) {
                            Modifier
                        } else {
                            Modifier.clickable { onCategoryClick(category) }
                        }
                    )

                    if (index < breadcrumbItems.lastIndex) {
                        Text(
                            text = ">",
                            modifier = Modifier.padding(horizontal = 6.dp),
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
