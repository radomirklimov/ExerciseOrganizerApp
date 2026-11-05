package com.organizer.presentation.screens.categories

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
    viewModel: OrganizerViewModel
) {
    val sport = categoryPath.firstOrNull()
    val subcategories = categoryPath.drop(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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

        // Category path to the current category
        if (subcategories.isNotEmpty()) {

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.padding(start = 56.dp),
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.Start
            ) {

                subcategories.forEachIndexed { index, category ->
                    Text(
                        text = category.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )

                    if (index < subcategories.lastIndex) {
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
