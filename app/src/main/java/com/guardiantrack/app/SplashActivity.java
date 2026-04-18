package com.guardiantrack.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

public class SplashActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        prefs = getSharedPreferences("gt_prefs", MODE_PRIVATE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String token = prefs.getString("gt_token", "");
            if (!token.isEmpty()) {
                checkBiometric();
            } else {
                goToMain();
            }
        }, 2000);
    }

    private void checkBiometric() {
        BiometricManager bm = BiometricManager.from(this);
        int canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG |
            BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt();
        } else {
            goToMain();
        }
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    goToMain();
                }
                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        code == BiometricPrompt.ERROR_USER_CANCELED) {
                        finish();
                    } else {
                        goToMain();
                    }
                }
                @Override
                public void onAuthenticationFailed() {}
            });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("GuardianTrack")
            .setSubtitle("Verify your identity to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build();

        prompt.authenticate(info);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
