@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appathy.seirihq.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.appathy.seirihq.data.Store

fun biometricAvailable(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

fun promptBiometric(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFail: (String) -> Unit
) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        onFail("この画面では指紋認証を利用できません")
        return
    }
    if (!biometricAvailable(context)) {
        onFail("指紋認証を利用できません")
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(errString.toString())
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("パスコードを使う")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(store: Store, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val setupMode = !store.pinSet

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (setupMode) "パスコードを設定" else "パスコードを入力",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            if (setupMode) {
                "素材を開くにはパスコードが必要です。4〜6桁の数字を設定してください。"
            } else {
                "素材はパスコードで保護されています。"
            },
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { input ->
                if (input.length <= 6 && input.all { it.isDigit() }) pin = input
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("パスコード") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )

        if (setupMode) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { input ->
                    if (input.length <= 6 && input.all { it.isDigit() }) confirm = input
                    message = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("確認のためもう一度") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        }

        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length >= 4,
            onClick = {
                if (setupMode) {
                    if (pin != confirm) {
                        message = "2回の入力が一致しません"
                    } else {
                        store.setPin(pin)
                    }
                } else {
                    if (!store.verifyPin(pin)) {
                        message = "パスコードが違います"
                        pin = ""
                    }
                }
            }
        ) { Text(if (setupMode) "設定して開く" else "解除") }

        if (!setupMode && store.biometricEnabled && biometricAvailable(context)) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    promptBiometric(
                        context = context,
                        title = "素材のロック解除",
                        subtitle = "指紋で解除します",
                        onSuccess = { store.unlock() },
                        onFail = { message = it }
                    )
                }
            ) { Text("指紋で解除") }
        }
    }
}
