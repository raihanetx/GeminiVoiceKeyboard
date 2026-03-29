package com.gemini.voicekeyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var micPermissionGranted = mutableStateOf(false)
    private var isKeyboardEnabled = mutableStateOf(false)
    private var isKeyboardSelected = mutableStateOf(false)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "MainActivity onCreate() called")

        // Initialize state values
        micPermissionGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isKeyboardEnabled.value = isKeyboardEnabled(this)
        isKeyboardSelected.value = isKeyboardActive(this)

        setContent {
            MaterialTheme {
                OnboardingScreen(
                    micPermissionGranted = micPermissionGranted.value,
                    isKeyboardEnabled = isKeyboardEnabled,
                    isKeyboardSelected = isKeyboardSelected,
                    onRequestMicPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update state when app returns to foreground (e.g., from settings)
        micPermissionGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isKeyboardEnabled.value = isKeyboardEnabled(this)
        isKeyboardSelected.value = isKeyboardActive(this)
        Log.d("MainActivity", "onResume: micPermission=$micPermissionGranted, keyboardEnabled=$isKeyboardEnabled, keyboardSelected=$isKeyboardSelected")
    }
}

@Composable
fun OnboardingScreen(
    micPermissionGranted: Boolean,
    isKeyboardEnabled: MutableState<Boolean>,
    isKeyboardSelected: MutableState<Boolean>,
    onRequestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(ApiKeyStore.get(context)) }
    var currentStep by remember { mutableIntStateOf(1) }

    // Refresh state when coming back to the app
    LaunchedEffect(Unit) {
        isKeyboardEnabled.value = isKeyboardEnabled(context)
        isKeyboardSelected.value = isKeyboardActive(context)

        // Auto-advance if steps are already completed
        if (!isKeyboardEnabled.value) {
            currentStep = 1
        } else if (!isKeyboardSelected.value) {
            currentStep = 2
        } else if (!micPermissionGranted) {
            currentStep = 3
        } else {
            currentStep = 4
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Gemini Voice Keyboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A73E8),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Set up your keyboard in 4 easy steps",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5F6368),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Step indicators
            StepIndicator(currentStep = currentStep, modifier = Modifier.padding(bottom = 32.dp))

            Spacer(modifier = Modifier.height(8.dp))

            // Step 1: Enable Keyboard
            StepCard(
                stepNumber = 1,
                title = "Enable Keyboard",
                description = "Enable 'Gemini Voice Keyboard' in your device's input method settings.",
                isActive = currentStep == 1,
                isCompleted = isKeyboardEnabled.value,
                buttonText = "Enable Keyboard",
                onButtonClick = {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Select Keyboard
            StepCard(
                stepNumber = 2,
                title = "Select Keyboard",
                description = "Switch to 'Gemini Voice Keyboard' as your active input method.",
                isActive = currentStep == 2,
                isCompleted = isKeyboardSelected.value,
                buttonText = "Select Keyboard",
                onButtonClick = {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3: Microphone Permission
            StepCard(
                stepNumber = 3,
                title = "Microphone Access",
                description = "Allow microphone access so the keyboard can listen to your voice.",
                isActive = currentStep == 3,
                isCompleted = micPermissionGranted,
                buttonText = if (micPermissionGranted) "Permission Granted" else "Grant Permission",
                onButtonClick = {
                    onRequestMicPermission()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 4: API Key
            StepCard(
                stepNumber = 4,
                title = "Enter API Key",
                description = "Enter your Gemini API key to enable voice-to-text transcription.",
                isActive = currentStep == 4,
                isCompleted = apiKey.isNotBlank(),
                buttonText = null,
                onButtonClick = null
            )

            if (currentStep == 4 || (isKeyboardEnabled.value && isKeyboardSelected.value && micPermissionGranted)) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("Paste your key here") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Get your key at aistudio.google.com/apikey",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5F6368),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (apiKey.isNotBlank()) {
                            ApiKeyStore.save(context, apiKey)
                            Toast.makeText(context, "API Key saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter your API key", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                ) {
                    Text("Save API Key", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show success and test button when all steps are complete
                if (apiKey.isNotBlank()) {
                    Text(
                        text = "Setup Complete! 🎉",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF34A853),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your Gemini Voice Keyboard is ready to use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5F6368),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            // Open browser to test keyboard
                            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://www.google.com")
                            }
                            context.startActivity(browserIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                    ) {
                        Text("Test Keyboard in Browser", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Refresh button
            TextButton(
                onClick = {
                    isKeyboardEnabled.value = isKeyboardEnabled(context)
                    isKeyboardSelected.value = isKeyboardActive(context)
                    if (!isKeyboardEnabled.value) {
                        currentStep = 1
                    } else if (!isKeyboardSelected.value) {
                        currentStep = 2
                    } else if (!micPermissionGranted) {
                        currentStep = 3
                    } else {
                        currentStep = 4
                    }
                }
            ) {
                Text("Refresh Status", color = Color(0xFF1A73E8))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..4) {
            StepCircle(
                number = i,
                isActive = i == currentStep,
                isCompleted = i < currentStep || (i == 1 && currentStep > 1) || (i == 2 && currentStep > 2) || (i == 3 && currentStep > 3)
            )
            if (i < 4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (i < currentStep) Color(0xFF1A73E8) else Color(0xFFDADCE0)
                        )
                )
            }
        }
    }
}

@Composable
fun StepCircle(number: Int, isActive: Boolean, isCompleted: Boolean) {
    val bgColor = when {
        isCompleted -> Color(0xFF1A73E8)
        isActive -> Color(0xFF1A73E8)
        else -> Color(0xFFDADCE0)
    }
    val textColor = when {
        isCompleted -> Color.White
        isActive -> Color.White
        else -> Color(0xFF5F6368)
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Text("✓", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(number.toString(), color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StepCard(
    stepNumber: Int,
    title: String,
    description: String,
    isActive: Boolean,
    isCompleted: Boolean,
    buttonText: String?,
    onButtonClick: (() -> Unit)?
) {
    val borderColor = when {
        isCompleted -> Color(0xFF34A853)
        isActive -> Color(0xFF1A73E8)
        else -> Color(0xFFDADCE0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Step number or check
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) Color(0xFF34A853) else if (isActive) Color(0xFF1A73E8) else Color(0xFFDADCE0)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(stepNumber.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF202124)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5F6368)
                )

                if (buttonText != null && onButtonClick != null && !isCompleted) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onButtonClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) Color(0xFF1A73E8) else Color(0xFFDADCE0)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(buttonText, fontSize = 13.sp)
                    }
                } else if (isCompleted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Completed ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF34A853),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabled = imm.enabledInputMethodList
    return enabled.any { it.packageName == context.packageName }
}

private fun isKeyboardActive(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    
    // Check if our IME is enabled
    val enabled = imm.enabledInputMethodList
    val isEnabled = enabled.any { it.packageName == context.packageName }
    
    // For now, we'll consider it "active" if it's enabled
    // The user will manually verify activation via the "Select Keyboard" step
    return isEnabled
}
