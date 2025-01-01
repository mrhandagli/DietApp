package com.dagli.dietapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dagli.dietapp.ui.theme.StandardButtonModifier

/**
 * WelcomeActivity, uygulama başlatıldığında
 * kullanıcıyı karşılayan ilk ekrandır.
 *
 * Kullanıcının daha önce bir kullanıcı adı ayarlayıp ayarlamadığını
 * kontrol ederek, uygun sonraki adımı başlatır.
 */
class WelcomeActivity : ComponentActivity() {
    /**
     * @param savedInstanceState Daha önce kaydedilen durum bilgilerini içerir (örnek: ekran döndürme).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kullanıcı tercihlerini (SharedPreferences) yükler
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        // "username_set" bayrağını kontrol ederek kullanıcı adının ayarlanıp ayarlanmadığına bakar
        val usernameSet = prefs.getBoolean("username_set", false)

        setContent {
            MaterialTheme {
                WelcomeScreen(
                    onGetStartedClick = {
                        // Eğer "Başla" butonuna tıklanırsa, UsernameActivity açılır
                        val intent = Intent(this, UsernameActivity::class.java)
                        startActivity(intent)
                        finish() // Bu aktiviteyi sonlandır
                    }
                )
            }
        }
    }
}

/**
 * WelcomeScreen, kullanıcının uygulamayla ilk kez karşılaştığı
 * karşılama arayüzünü (splash veya hoş geldiniz) temsil eden composable fonksiyondur.
 *
 * @param onGetStartedClick "Başla" butonuna tıklandığında çağrılan callback.
 */
@Composable
fun WelcomeScreen(onGetStartedClick: () -> Unit) {
    // Arka plan için dikey (vertical) geçiş (gradient) tanımı
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
    )

    // Box, tüm ekranı kaplayacak şekilde arka planı uygular
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
            .padding(24.dp)
    ) {
        // Ekran içeriğini dikey ve yatay olarak ortalayan Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Uygulama logosu veya illüstrasyon
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "Uygulama Logosu",
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 32.dp),
                contentScale = ContentScale.Fit
            )

            // Hoş geldiniz başlığı
            Text(
                text = "Diyet Takip Uygulamanızla Tanışın",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Alt başlık veya açıklama metni
            Text(
                text = "Yemeklerini takip et, yemek tarifleri keşfet ve diyetine sadık kal. " +
                        "Haydi kuruluma başlayalım.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // "Başla" butonu
            ElevatedButton(
                onClick = onGetStartedClick,
                modifier = StandardButtonModifier, // Uygulamanın kendi tanımladığı düzen
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Başla",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
