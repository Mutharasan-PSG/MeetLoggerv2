package com.example.meetloggerv2.ui.audio.util

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.meetloggerv2.core.theme.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

sealed class WizardStep {
    object SpeakerSelection : WizardStep()
    object SpeakerInput : WizardStep()
    object FollowUpSelection : WizardStep()
}

@Composable
fun AudioProcessingDialog(
    userFiles: List<String>,
    onDismiss: () -> Unit,
    onProcessingConfirmed: (speakers: List<String>, followUp: String) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf<WizardStep>(WizardStep.SpeakerSelection) }
    val speakerList = remember { mutableStateListOf<String>("") }
    var hasSpeakers by remember { mutableStateOf<Boolean?>(null) }
    var isFollowUp by remember { mutableStateOf<Boolean?>(null) }
    var selectedFileIndex by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .animateContentSize(animationSpec = tween(300))
            ) {
                when (currentStep) {
                    WizardStep.SpeakerSelection -> {
                        Text(
                            text = "Speaker Diarization",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Identify different voices and attribute transcripts to specific speakers in the final document.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Custom Option Capsules
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OptionCapsule(
                                label = "Yes, identify speakers",
                                selected = hasSpeakers == true,
                                onClick = { hasSpeakers = true },
                                modifier = Modifier.weight(1f)
                            )
                            OptionCapsule(
                                label = "No, skip",
                                selected = hasSpeakers == false,
                                onClick = { hasSpeakers = false },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val cancelInteractionSource = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = onDismiss,
                                interactionSource = cancelInteractionSource,
                                modifier = Modifier.weight(1f).height(48.dp).pressScale(cancelInteractionSource),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }
                            val next1InteractionSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = {
                                    if (hasSpeakers == null) {
                                        Toast.makeText(context, "Please select an option", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (hasSpeakers == true) {
                                        currentStep = WizardStep.SpeakerInput
                                    } else {
                                        currentStep = WizardStep.FollowUpSelection
                                    }
                                },
                                interactionSource = next1InteractionSource,
                                modifier = Modifier.weight(1f).height(48.dp).pressScale(next1InteractionSource),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Next", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    WizardStep.SpeakerInput -> {
                        val speakerFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            delay(100)
                            speakerFocusRequester.requestFocus()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val back1InteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { currentStep = WizardStep.SpeakerSelection },
                                interactionSource = back1InteractionSource,
                                modifier = Modifier.pressScale(back1InteractionSource)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Speaker Names",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add names for speakers in this recording (maximum 10).",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                speakerList.forEachIndexed { index, name ->
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { speakerList[index] = it },
                                        label = { Text("Speaker ${index + 1}") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(speakerFocusRequester) else Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        val allFilled = speakerList.all { it.isNotBlank() } && speakerList.isNotEmpty()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val addSpeakerInteractionSource = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = { if (speakerList.size < 10) speakerList.add("") },
                                enabled = allFilled && speakerList.size < 10,
                                interactionSource = addSpeakerInteractionSource,
                                modifier = Modifier.weight(1f).height(48.dp).pressScale(addSpeakerInteractionSource),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", fontWeight = FontWeight.Bold)
                                }
                            }
                            val next2InteractionSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = {
                                    if (allFilled) {
                                        currentStep = WizardStep.FollowUpSelection
                                    }
                                },
                                enabled = allFilled,
                                interactionSource = next2InteractionSource,
                                modifier = Modifier.weight(1f).height(48.dp).pressScale(next2InteractionSource),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Next", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    WizardStep.FollowUpSelection -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val back2InteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = {
                                    if (hasSpeakers == true) {
                                        currentStep = WizardStep.SpeakerInput
                                    } else {
                                        currentStep = WizardStep.SpeakerSelection
                                    }
                                },
                                interactionSource = back2InteractionSource,
                                modifier = Modifier.pressScale(back2InteractionSource)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Follow-up Context",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Link this transcript to a previous meeting or report for context-aware summaries.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Custom Option Capsules
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OptionCapsule(
                                label = "Yes, select file",
                                selected = isFollowUp == true,
                                onClick = { isFollowUp = true },
                                modifier = Modifier.weight(1f)
                            )
                            OptionCapsule(
                                label = "No context",
                                selected = isFollowUp == false,
                                onClick = { isFollowUp = false },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        var expandedDropdown by remember { mutableStateOf(false) }

                        if (isFollowUp == true) {
                            if (userFiles.isEmpty()) {
                                Text(
                                    text = "No previous files available",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val currentText = userFiles.getOrNull(selectedFileIndex)?.substringBeforeLast(".") ?: "Select context file..."
                                    val selectFileInteractionSource = remember { MutableInteractionSource() }
                                    OutlinedButton(
                                        onClick = { expandedDropdown = true },
                                        interactionSource = selectFileInteractionSource,
                                        modifier = Modifier.fillMaxWidth().height(48.dp).pressScale(selectFileInteractionSource),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(currentText, fontWeight = FontWeight.SemiBold)
                                    }
                                    DropdownMenu(
                                        expanded = expandedDropdown,
                                        onDismissRequest = { expandedDropdown = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        userFiles.forEachIndexed { idx, file ->
                                            val itemInteractionSource = remember { MutableInteractionSource() }
                                            DropdownMenuItem(
                                                text = { Text(file.substringBeforeLast("."), fontWeight = FontWeight.Medium) },
                                                interactionSource = itemInteractionSource,
                                                modifier = Modifier.pressScale(itemInteractionSource),
                                                onClick = {
                                                    selectedFileIndex = idx
                                                    expandedDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        val canProceed = isFollowUp == false || (isFollowUp == true && userFiles.isNotEmpty())

                        val processInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                if (canProceed) {
                                    val speakers = if (hasSpeakers == true) speakerList.filter { it.isNotBlank() } else emptyList()
                                    val followUp = if (isFollowUp == true) userFiles.getOrNull(selectedFileIndex) ?: "" else ""
                                    onProcessingConfirmed(speakers, followUp)
                                }
                            },
                            enabled = canProceed,
                            shape = RoundedCornerShape(24.dp),
                            interactionSource = processInteractionSource,
                            modifier = Modifier.fillMaxWidth().height(48.dp).pressScale(processInteractionSource),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Process & Finish", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptionCapsule(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 2.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
        interactionSource = interactionSource,
        modifier = modifier.height(60.dp).pressScale(interactionSource)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
