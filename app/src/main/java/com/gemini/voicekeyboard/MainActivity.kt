package com.gemini.voicekeyboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SimpleSetupScreen()
            }
        }
    }
}

@Composable
fun SimpleSetupScreen() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(ApiKeyStore.get(context)) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Gemini Voice Keyboard Setup",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("Get your key at aistudio.google.com/apikey") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save API Key")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "After saving your API key:\n1. Go to Settings → System → Languages & input → On-screen keyboard\n2. Tap 'Manage on-screen keyboards'\n3. Toggle ON 'Gemini Voice Keyboard'\n4. Open any app and tap where you can type\n5. Tap the microphone icon and speak!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Keyboard Settings")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "✨ Powered by Gemini 3.1 Flash Live (Released March 26, 2026)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
