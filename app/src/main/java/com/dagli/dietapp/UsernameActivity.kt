package com.dagli.dietapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * UsernameActivity, uygulamanın ilk adımı olarak
 * kullanıcıdan bir kullanıcı adı alır ve sonrasında
 * [ExchangeActivity]'ye yönlendirir.
 */
class UsernameActivity : ComponentActivity() {

    /**
     * @param savedInstanceState Daha önce kaydedilen durum verilerini içerir (ör. ekran döndürme).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI içeriğini Compose ile ayarlar
        setContent {
            MaterialTheme {
                UsernameScreen(
                    onNextClick = { username ->
                        // Kullanıcı adını kaydeder ve bir sonraki ekrana geçer
                        saveUsername(username)
                        val intent = Intent(this, ExchangeActivity::class.java)
                        startActivity(intent)
                        finish() // Bu aktiviteyi kapat
                    }
                )
            }
        }
    }

    /**
     * Kullanıcı adını SharedPreferences içine kaydeden fonksiyon.
     *
     * @param username Kaydedilecek kullanıcı adı.
     */
    private fun saveUsername(username: String) {
        // Kullanıcı tercihleri (SharedPreferences) alınır
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            // Kullanıcı adının ayarlandığını işaretler
            putBoolean("username_set", true)
            // Asıl kullanıcı adını saklar
            putString("USERNAME_KEY", username)
            apply()
        }
    }
}

/**
 * UsernameScreen, kullanıcıdan bir metin (kullanıcı adı) girmesini isteyen composable.
 *
 * @param onNextClick Kullanıcı geçerli bir kullanıcı adı girdiğinde çalışacak callback.
 */
@Composable
fun UsernameScreen(onNextClick: (String) -> Unit) {
    // Arka plan için dikey (vertical) geçiş rengi
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
    )

    // Yüzey (Surface) oluşturma
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundBrush),
        color = Color.Transparent // Böylece arka planın degrade görünümü korunur
    ) {
        // Kullanıcının girdiği metni tutan durum (state)
        var username by remember { mutableStateOf("") }
        // Kullanıcının boş değer girmesi durumunu kontrol etmek için hata durumu
        var isError by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hoş geldiniz görseli
            Image(
                painter = painterResource(id = R.drawable.ic_welcome_illustration),
                contentDescription = "Hoş Geldiniz Görseli",
                modifier = Modifier
                    .size(300.dp)
                    .padding(bottom = 32.dp),
                contentScale = ContentScale.Fit
            )

            // Kullanıcıya sorulan başlık
            Text(
                text = "Sana nasıl hitap etmeliyiz?",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Kullanıcı adı girişi (TextField)
            UsernameInputField(
                username = username,
                onUsernameChange = { username = it },
                isError = isError
            )

            // Eğer isError true ise, kullanıcı boş bir isim girmiş demektir
            if (isError) {
                Text(
                    text = "Lütfen adını gir",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // "Devam Et" butonu
            ElevatedButton(
                onClick = {
                    // Eğer kullanıcı adı boşsa, hata durumunu güncelle
                    if (username.isBlank()) {
                        isError = true
                    } else {
                        onNextClick(username.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Devam Et",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/**
 * UsernameInputField, bir [OutlinedTextField] composable kullanarak kullanıcı adı girişini sağlar.
 *
 * @param username Girilen kullanıcı adını temsil eden metin değeri.
 * @param onUsernameChange Kullanıcı adı her değiştiğinde tetiklenen callback.
 * @param isError Kullanıcının geçersiz (örneğin boş) bir değer girdiğini belirtir.
 */
@Composable
fun UsernameInputField(
    username: String,
    onUsernameChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = username,
        onValueChange = {
            onUsernameChange(it)
        },
        label = { Text("Adınız") },
        isError = isError,
        modifier = Modifier
            .fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        placeholder = { Text(text = "Adınızı girebilirsiniz") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
            errorBorderColor = MaterialTheme.colorScheme.error
        )
    )
}
