package com.example.ui

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit,
    onOpenSafPicker: (() -> Unit)? = null,
    onCreateTestNomediaFolder: () -> Unit
) {
    val strings = LocalAppStrings.current
    var pathInput by remember { mutableStateOf(currentPath) }

    val popularFolders = remember {
        val root = Environment.getExternalStorageDirectory().absolutePath
        listOf(
            "Movies" to "$root/Movies",
            "Download" to "$root/Download",
            "DCIM" to "$root/DCIM",
            ".hidden_videos" to "$root/.hidden_videos"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = VaultPrimary
                )
                Text(
                    text = strings.folderPickerTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = VaultTextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = strings.folderPickerDesc,
                    fontSize = 12.sp,
                    color = VaultTextSecondary,
                    lineHeight = 16.sp
                )

                // SAF system picker button
                if (onOpenSafPicker != null) {
                    Button(
                        onClick = {
                            onOpenSafPicker()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultPrimary,
                            contentColor = VaultOnPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.btnSafPicker, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    HorizontalDivider(color = VaultBorder.copy(alpha = 0.5f))
                }

                Text(
                    text = strings.manualPathHeader,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = VaultTextPrimary
                )

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = { Text(strings.pathFieldLabel) },
                    placeholder = { Text("/storage/emulated/0/Movies") },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultPrimary,
                        unfocusedBorderColor = VaultBorder,
                        focusedTextColor = VaultTextPrimary,
                        unfocusedTextColor = VaultTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = strings.popularFoldersHeader,
                    fontSize = 11.sp,
                    color = VaultTextSecondary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    popularFolders.forEach { (label, path) ->
                        Surface(
                            color = if (pathInput == path) VaultPrimary.copy(alpha = 0.2f) else VaultBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (pathInput == path) VaultPrimary else VaultBorder),
                            onClick = { pathInput = path },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VaultTextPrimary
                                    )
                                    Text(
                                        text = path,
                                        fontSize = 10.sp,
                                        color = VaultTextSecondary,
                                        maxLines = 1
                                    )
                                }
                                if (pathInput == path) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = VaultPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = VaultBorder.copy(alpha = 0.5f))

                // Test creator button
                OutlinedButton(
                    onClick = {
                        onCreateTestNomediaFolder()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultAccentCyan),
                    border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.btnCreateNomedia, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pathInput.isNotBlank()) {
                        onFolderSelected(pathInput.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary)
            ) {
                Text(strings.select, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = VaultTextSecondary)
            }
        },
        containerColor = VaultSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
