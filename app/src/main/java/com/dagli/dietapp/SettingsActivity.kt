package com.dagli.dietapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `SettingsActivity`, kullanıcı ayarlarını (örneğin akıllı tartı
 * yapılandırması, kullanıcı adı) yönetmek için bir arayüz sunar.
 */
class SettingsActivity : ComponentActivity() {
    // OkHttpClient, HTTP isteklerini göndermek için kullanılır
    private val client = OkHttpClient()

    /**
     * Aktifken ilk çalışan fonksiyon. Arayüzün oluşturulması ve
     * `SettingsScreen` composable'ının gösterilmesi burada gerçekleşir.
     *
     * @param savedInstanceState Ekran döndürme gibi durumlarda eski verileri tutar.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(
                    onBack = { finish() },
                    context = this,
                    client = client
                )
            }
        }
    }
}

/**
 * `SettingsScreen`, akıllı tartı Wi-Fi yapılandırması, kullanıcı adı gibi
 * ayarları yönetmek için tasarlanmış bir Compose arayüzüdür.
 *
 * @param onBack Geri tuşuna basıldığında veya menü butonu tıklandığında çağrılacak işlev.
 * @param context Android `Context` nesnesi; SharedPreferences'e ve sistem kaynaklarına erişim sağlar.
 * @param client HTTP isteklerini göndermek için kullanılacak `OkHttpClient` örneği.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    context: Context,
    client: OkHttpClient
) {
    // Wi-Fi SSID ve şifresi için state
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    // Tartı bağlantı ve mesaj durumu
    var scaleConnected by remember { mutableStateOf(false) }
    var scaleMessage by remember { mutableStateOf("") }

    // SharedPreferences üzerinden tartının aktif olup olmadığını kaydeder/yükler
    val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    var scaleEnabled by remember {
        mutableStateOf(
            sharedPref.getBoolean("scale_enabled", false)
        )
    }

    // Tartıyla yapılan işlemlerin sonuç mesajı
    var scaleActionResult by remember { mutableStateOf("") }

    // Kullanıcı adı (username) alanları
    var username by remember { mutableStateOf("") }
    var usernameMessage by remember { mutableStateOf("") }

    // Ekran oluşturulurken kullanıcı adını SharedPreferences'tan yükle
    LaunchedEffect(Unit) {
        username = sharedPref.getString("USERNAME_KEY", "") ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kurulum", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // Geri ikonu
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // ---------- Akıllı Tartı Bölümü ----------
                Text("Akıllı Tartı", style = MaterialTheme.typography.headlineMedium)

                // Tartıyı aktifleştirmek için Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tartıyı Aktifleştir", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = scaleEnabled,
                        onCheckedChange = { isChecked ->
                            scaleEnabled = isChecked
                            sharedPref.edit()
                                .putBoolean("scale_enabled", isChecked)
                                .apply()
                        }
                    )
                }

                // Wi-Fi bilgileri için TextField'lar
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Evin Wi-Fi Ağının Adı") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Evin Wi-Fi Ağının Şifresi") },
                    modifier = Modifier.fillMaxWidth()
                )

                // "Bilgileri Kaydet" ve "Tartı Bağlantısını Kontrol Et" butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            sendScaleCredentials(client, ssid, pass) { success, msg ->
                                scaleMessage = msg
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bilgileri Kaydet")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedButton(
                        onClick = {
                            checkScaleConnection(client) { success, msg ->
                                scaleConnected = success
                                scaleMessage = msg
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tartı Bağlantısını Kontrol Et")
                    }
                }

                // Tartı bağlantı veya Wi-Fi ayarlarından gelen sonuç mesajı
                if (scaleMessage.isNotEmpty()) {
                    Text(
                        text = scaleMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (scaleConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                // Eğer tartı bağlıysa ek kontrolleri göster
                if (scaleConnected) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Ek Tartı Kontrolleri", style = MaterialTheme.typography.titleMedium)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Tartıdan gelen ek işlemlerin sonucu
                            if (scaleActionResult.isNotEmpty()) {
                                Text(
                                    text = scaleActionResult,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Dara (Tare) butonu
                                Button(
                                    onClick = {
                                        tareScale(client) { msg ->
                                            scaleActionResult = msg
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Dara")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Ağırlığı Oku
                                Button(
                                    onClick = {
                                        readScaleValue(client) { msg ->
                                            scaleActionResult = msg
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Ağırlığı Oku")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Ekranı Aç
                                OutlinedButton(
                                    onClick = {
                                        turnScreenOn(client) { msg ->
                                            scaleActionResult = msg
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Ekranı Aç")
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Ekranı Kapat
                                OutlinedButton(
                                    onClick = {
                                        turnScreenOff(client) { msg ->
                                            scaleActionResult = msg
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Ekranı Kapat")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Derin Uyku
                            Button(
                                onClick = {
                                    sleepScale(client) { msg ->
                                        scaleActionResult = msg
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Derin Uykuya Geç")
                            }
                        }
                    }
                }

                // ---------- Kullanıcı Adı Bölümü ----------
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Kullanıcı Adı", style = MaterialTheme.typography.titleMedium)

                var isUsernameError by remember { mutableStateOf(false) }

                // `UsernameInputField`, kullanıcı adını girmek için kullanılan özel composable
                UsernameInputField(
                    username = username,
                    onUsernameChange = {
                        // Kullanıcı yazmaya başladığında hatayı sıfırla
                        isUsernameError = false
                        username = it
                    },
                    isError = isUsernameError
                )

                Button(
                    onClick = {
                        if (username.isBlank()) {
                            isUsernameError = true
                        } else {
                            // Kullanıcı adını SharedPreferences'e kaydet
                            sharedPref.edit().putString("USERNAME_KEY", username).apply()
                            usernameMessage = "Kullanıcı adınız güncellendi: $username"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Kullanıcı Adını Kaydet")
                }

                if (usernameMessage.isNotEmpty()) {
                    Text(
                        text = usernameMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

/**
 * Tartı Wi-Fi kimlik bilgilerini (SSID ve şifre) gönderir.
 *
 * @param client HTTP isteklerini göndermek için kullanılan `OkHttpClient`.
 * @param ssid Wi-Fi ağının adı.
 * @param pass Wi-Fi ağının şifresi.
 * @param callback Sonuç başarılıysa `true`, hata mesajıyla `false` döndüren fonksiyon.
 */
private fun sendScaleCredentials(client: OkHttpClient, ssid: String, pass: String, callback: (Boolean, String) -> Unit) {
    val url = "http://192.168.4.1/setCredentials"
    val formBody = FormBody.Builder()
        .add("ssid", ssid)
        .add("pass", pass)
        .build()

    val request = Request.Builder()
        .url(url)
        .post(formBody)
        .build()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                callback(true, "Bilgiler kaydedildi. Tartı yeniden başlayacak. Wi-Fi'nize yeniden bağlanabilirsiniz.")
            } else {
                callback(false, "Bilgiler kaydedilemedi. Hata kodu: ${response.code}")
            }
        } catch (e: Exception) {
            callback(false, "Hata: ${e.message}")
        }
    }.start()
}

/**
 * Tartıya bağlanıp bağlanamadığını kontrol eder.
 *
 * @param client HTTP isteklerini göndermek için kullanılan `OkHttpClient`.
 * @param callback Tartı bağlıysa `true` ve bir mesaj, değilse `false` ve hata mesajı.
 */
private fun checkScaleConnection(client: OkHttpClient, callback: (Boolean, String) -> Unit) {
    val url = "http://smartscale.local/value"

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val weight = response.body?.string()?.trim() ?: "Unknown"
                callback(true, "Tartı bağlı! Şu anki ağırlık: $weight g")
            } else {
                callback(false, "Bağlanılamadı. Hata kodu: ${response.code}")
            }
        } catch (e: Exception) {
            callback(false, "Bağlanılamadı: ${e.message}")
        }
    }.start()
}

/**
 * Tartıyı sıfırlama (Tare) işlemi yapar.
 *
 * @param client HTTP isteklerini göndermek için kullanılan `OkHttpClient`.
 * @param callback İşlem sonucunun mesajını döndürür.
 */
fun tareScale(client: OkHttpClient, callback: (String) -> Unit) {
    val url = "http://smartscale.local/tare"

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                callback("Tartı sıfırlandı (Tare).")
            } else {
                callback("Tare başarısız. Hata kodu: ${response.code}")
            }
        } catch (e: Exception) {
            callback("Tare işlemi başarısız: ${e.message}")
        }
    }.start()
}

/**
 * Tartıdan güncel ağırlık verisini okur.
 *
 * @param client HTTP isteklerini göndermek için kullanılan `OkHttpClient`.
 * @param callback Okunan ağırlığı veya hata mesajını döndürür.
 */
fun readScaleValue(client: OkHttpClient, callback: (String) -> Unit) {
    val url = "http://smartscale.local/value"

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val weight = response.body?.string()?.trim() ?: "Unknown"
                callback("Şu anki ağırlık: $weight g")
            } else {
                callback("Ağırlık okunamadı. Hata kodu: ${response.code}")
            }
        } catch (e: Exception) {
            callback("Ağırlık okunamadı: ${e.message}")
        }
    }.start()
}

/**
 * Tartının ekranını açar.
 *
 * @param client HTTP isteklerini göndermek için kullanılan `OkHttpClient`.
 * @param callback İşlem sonucunun mesajını döndürür.
 */
fun turnScreenOn(client: OkHttpClient, callback: (String) -> Unit) {
    val url = "http://smartscale.local/screenOn"

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                callback("Ekran Açıldı.")
            } else {
                callback("Ekran açılamadı. Hata kodu: ${response.code}")
            }
        } catch (e: Exception) {
            callback("Ekran açılamadı: ${e.message}")
        }
    }.start()
}

/**
 * Tartının ekranını kapatır.
 *
 * @param client HTTP isteklerini göndermek için kullanılan `OkHttpClient`.
 * @param callback İşlem sonucunun mesajını döndürür.
 */
fun turnScreenOff(client: OkHttpClient, callback: (String) -> Unit) {
    val url = "http://smartscale.local/screenOff"

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                callback("Ekran Kapatıldı.")
            } else {
                callback("Ekran kapatılamadı. Hata kodu: ${response.code}")
            }
        } catch (e: Exception) {
            callback("Ekran kapatılamadı: ${e.message}")
        }
    }.start()
}

/**
 * Tartıyı "derin uyku" (sleep) moduna geçirir.
 *
 * @param client HTTP isteklerini göndermek için kullanılan `OkHttpClient`.
 * @param callback İşlem sonucunun mesajını döndürür.
 */
fun sleepScale(client: OkHttpClient, callback: (String) -> Unit) {
    val url = "http://smartscale.local/sleep"

    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    Thread {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                callback("Tartı derin uykuya geçiyor.")
            } else {
                callback("Derin uyku başarısız. Hata kodu: ${response.code}")
            }
        } catch (e: Exception) {
            callback("Derin uykuya geçilemedi: ${e.message}")
        }
    }.start()
}
