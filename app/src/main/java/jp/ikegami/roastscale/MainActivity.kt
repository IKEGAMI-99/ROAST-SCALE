package jp.ikegami.roastscale

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.ikegami.roastscale.data.Measurement
import jp.ikegami.roastscale.data.Profile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Night = Color(0xFF0B0F1A)
private val Surface = Color(0xFF171E2C)
private val Accent = Color(0xFFB8FF6A)
private val Muted = Color(0xFFABB7C8)
private val Purple = Color(0xFFC6A8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: RoastViewModel = viewModel()
            RoastApp(viewModel)
        }
    }
}

@Composable
private fun RoastApp(vm: RoastViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    // Request both approximate + precise on Android 12+; requesting precise
    // alone can be ignored. BLUETOOTH_CONNECT covers BluetoothAdapter.isEnabled.
    val required = remember {
        if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) else arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (required.all { granted[it] == true }) vm.startScan() else vm.reportPermissionDenied()
    }
    val requestScan: () -> Unit = {
        // Android's BLE scanner may register successfully yet return zero results
        // with the system-wide Location switch off (especially vendor stacks).
        val locationManager = context.getSystemService(LocationManager::class.java)
        val locationOn = if (Build.VERSION.SDK_INT >= 28) locationManager?.isLocationEnabled == true
            else locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        if (!locationOn) {
            vm.reportLocationDisabled()
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else if (required.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }) {
            vm.startScan()
        } else {
            permissionLauncher.launch(required)
        }
    }

    val colors = darkColorScheme(
        primary = Accent, onPrimary = Night,
        background = Night, surface = Surface,
        onSurface = Color.White, secondary = Purple,
        onBackground = Color.White
    )
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF111724)) {
                    listOf("⚖️" to "測定", "📈" to "履歴", "⚙️" to "設定").forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = { page = index },
                            icon = { Text(item.first) },
                            label = { Text(item.second) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent, selectedTextColor = Accent,
                                indicatorColor = Color(0xFF293C32)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF182337), Night, Night)))
                    .padding(padding)
            ) {
                state.message?.let { message ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF38402C)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, Modifier.weight(1f).padding(vertical = 10.dp), color = Color.White)
                        TextButton(onClick = vm::clearMessage) { Text("閉じる") }
                    }
                }
                when (page) {
                    0 -> Dashboard(state, requestScan, vm::stopScan)
                    1 -> HistoryScreen(state.measurements)
                    else -> SettingsScreen(state, vm)
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String) {
    Column(Modifier.padding(top = 22.dp, bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) { Column(Modifier.padding(18.dp), content = content) }
}

private fun format(value: Double?, suffix: String = ""): String =
    if (value == null) "—" else String.format(Locale.JAPAN, "%.1f", value) + suffix

private fun date(millis: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN).format(Date(millis))

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Dashboard(state: RoastUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val recent = state.measurements.firstOrNull()
    val previous = state.measurements.getOrNull(1)
    val delta = if (recent != null && previous != null) recent.weightKg - previous.weightKg else null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        TitleBlock("ROAST SCALE", "YOUR SCALE. YOUR DATA. NO EXCUSES.")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CURRENT WEIGHT", style = MaterialTheme.typography.labelMedium, color = Accent)
                    Spacer(Modifier.height(10.dp))
                    Text(if (recent == null) "--.- kg" else format(recent.weightKg, " kg"),
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (delta == null) "記録なし" else (if (delta > 0) "+" else "") +
                        format(delta, " kg"),
                        color = when {
                            delta == null -> Muted
                            abs(delta) <= 0.1 -> Accent
                            else -> Purple
                        }, fontWeight = FontWeight.Bold)
                    Text("前回比", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
            Spacer(Modifier.height(18.dp))
            TrendBars(state.measurements)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF334052))
            Spacer(Modifier.height(10.dp))
            Row {
                Metric("BMI", format(recent?.composition?.bmi), Modifier.weight(1f))
                Metric("体脂肪率（推定）", format(recent?.composition?.fatPercent, " %"), Modifier.weight(1f))
            }
            Row {
                Metric("筋肉量（推定）", format(recent?.composition?.muscleKg, " kg"), Modifier.weight(1f))
                Metric("体水分率（推定）", format(recent?.composition?.waterPercent, " %"), Modifier.weight(1f))
            }
            Row {
                Metric("骨量（推定）", format(recent?.composition?.boneKg, " kg"), Modifier.weight(1f))
                Metric("基礎代謝（推定）", format(recent?.composition?.bmrKcal, " kcal"), Modifier.weight(1f))
            }
            if (recent?.impedanceOhm != null) {
                Text("インピーダンス " + recent.impedanceOhm + " Ω",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("※体脂肪・筋肉量等は推定値です。医療用途には使用しないでください。",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }

        Panel {
            Text("BLUETOOTH / XMTZC02HM", color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(state.status, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = if (state.scanning) onStop else onStart,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.scanning) "測定を停止" else "BLE測定を開始") }
            Text("体重が安定したら自動保存。ペアリング不要です。",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }

        Panel {
            Text("🔎 BLE受信診断", color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val d = state.bleDiagnostics
            Text("端末: " + Build.MANUFACTURER + " " + Build.MODEL +
                "  / Android " + Build.VERSION.RELEASE,
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("位置情報: " + if (d.locationEnabled) "ON" else "OFF",
                color = if (d.locationEnabled) Accent else Color(0xFFFFA58A))
            Text("近くのデバイス: " + if (d.scanPermission && d.connectPermission) "許可" else "未許可",
                color = if (d.scanPermission && d.connectPermission) Accent else Color(0xFFFFA58A))
            Text("正確な位置情報: " + if (d.fineLocationPermission) "許可" else "未許可",
                color = if (d.fineLocationPermission) Accent else Color(0xFFFFA58A))
            Text("経過時間: " + d.secondsSinceStart + "秒  /  状態: " + d.lastResult,
                color = Muted, style = MaterialTheme.typography.bodySmall)
            if (!d.locationEnabled) {
                Text("システムの位置情報がOFFです。BLEスキャン結果が返らないことがあります。",
                    color = Color(0xFFFFA58A), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("端末の位置情報設定を開く")
            }
            Spacer(Modifier.height(8.dp))
            Text("周辺BLE広告: " + state.bleDiagnostics.packets +
                " 件  /  Xiaomi (181B): " + state.bleDiagnostics.scalePackets +
                " 件  /  解析成功: " + state.bleDiagnostics.validPackets + " 件",
                color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            if (state.bleDiagnostics.scalePackets > 0) {
                Text("受信機器: " + state.bleDiagnostics.lastDevice, color = Muted)
                Text("判定: " + state.bleDiagnostics.lastResult, color = Accent)
                Text("生データ (HEX):", color = Muted,
                    style = MaterialTheme.typography.labelSmall)
                Text(state.bleDiagnostics.lastFrame, color = Color.White,
                    style = MaterialTheme.typography.bodySmall)
            } else if (state.bleDiagnostics.packets > 0) {
                Text("周辺のBLE信号は受信中ですが、体組成計の広告は未検出です。計測中に端末を近づけてください。",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("0件のままならBluetooth・近くのデバイス権限・端末の位置情報設定を確認してください。",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.bleDiagnostics.nearbyDevices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("近くのBLE機器", color = Muted, style = MaterialTheme.typography.labelSmall)
                state.bleDiagnostics.nearbyDevices.forEach { item ->
                    Text("• " + item, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Panel {
            Text("💀  GEMMA 4 / ROAST REPORT", color = Purple, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    state.aiStatus.isNotBlank() -> state.aiStatus
                    recent == null -> "最初の測定を待っています。数字からは逃げられません。"
                    recent.comment.isBlank() -> "測定データは記録済み。AIモデルを準備すると毒舌講評が使えます。"
                    else -> recent.comment
                },
                color = Color.White, style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(10.dp))
            Text("毒舌レベル " + state.roastLevel + " / 4  ·  ローカル推論",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TrendBars(data: List<Measurement>) {
    val values = data.take(14).reversed().map { it.weightKg }
    if (values.isEmpty()) {
        Text("グラフは測定後に表示されます", color = Muted)
        return
    }
    val low = values.minOrNull() ?: 0.0
    val high = values.maxOrNull() ?: 1.0
    Text("WEIGHT TREND · LAST " + values.size, color = Muted,
        style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().height(94.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        values.forEachIndexed { i, value ->
            val range = (high - low).coerceAtLeast(0.2)
            val fraction = ((value - low) / range).toFloat().coerceIn(0f, 1f)
            Box(Modifier.weight(1f).height((20f + fraction * 68f).dp)
                .background(if (i == values.lastIndex) Accent else Color(0xFF526984),
                    RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
        }
    }
}

@Composable
private fun HistoryScreen(history: List<Measurement>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TitleBlock("測定履歴", "測定データとGemmaの講評を端末内に保存")
        if (history.isEmpty()) {
            Panel { Text("まだ測定履歴がありません", color = Muted) }
        }
        history.forEach { row ->
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(date(row.measuredAt), color = Muted,
                            style = MaterialTheme.typography.labelSmall)
                        Text(format(row.weightKg, " kg"), color = Color.White,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("体脂肪 " + format(row.composition?.fatPercent, " %"), color = Accent)
                        Text("BMI " + format(row.composition?.bmi), color = Muted)
                    }
                }
                if (row.comment.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF354257))
                    Spacer(Modifier.height(10.dp))
                    Text("💀 " + row.comment, color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsScreen(state: RoastUiState, vm: RoastViewModel) {
    var height by remember(state.profile.heightCm) {
        mutableStateOf(state.profile.heightCm.takeIf { it > 0 }?.toString() ?: "")
    }
    var age by remember(state.profile.age) {
        mutableStateOf(state.profile.age.takeIf { it > 0 }?.toString() ?: "")
    }
    var sex by remember(state.profile.sex) { mutableStateOf(state.profile.sex) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        TitleBlock("設定", "個人データは端末から出さない")
        Panel {
            Text("測定プロフィール", color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = height, onValueChange = { height = it.filter(Char::isDigit).take(3) },
                label = { Text("身長 cm") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = age, onValueChange = { age = it.filter(Char::isDigit).take(3) },
                label = { Text("年齢") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text("体組成推定式に使う性別", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = sex == "male", onClick = { sex = "male" },
                    label = { Text("男性用の式") })
                FilterChip(selected = sex == "female", onClick = { sex = "female" },
                    label = { Text("女性用の式") })
            }
            Spacer(Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.saveProfile(Profile(height.toIntOrNull() ?: 0, age.toIntOrNull() ?: 0, sex))
                }
            ) { Text("プロフィールを保存") }
        }

        Panel {
            Text("毒舌レベル", color = Purple, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(listOf("","やさしめ","辛口","毒舌","地獄")[state.roastLevel], color = Color.White)
            Slider(value = state.roastLevel.toFloat(), onValueChange = { vm.setRoast(it.roundToInt()) },
                valueRange = 1f..4f, steps = 2)
            Text("数値には厳しく、人格や体型への侮辱はしない設定です。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        Panel {
            Text("Gemma 4 E4B / LiteRT-LM", color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(if (state.modelReady) "モデル準備完了（オフライン推論）"
                else "初回のみ約3.66GBをダウンロード", color = Muted)
            if (state.modelDownloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { state.modelProgress / 100f },
                    modifier = Modifier.fillMaxWidth())
                Text(state.modelProgress.toString() + " %", color = Muted)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.modelReady && !state.modelDownloading,
                onClick = vm::downloadModel
            ) { Text(if (state.modelReady) "モデル取得済み" else "E4Bモデルをダウンロード") }
            Text("取得元: Hugging Face / LiteRT Community",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }

        Panel {
            Text("GitHub Releases / アプリ更新", color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("現在: v" + BuildConfig.VERSION_NAME, color = Muted)
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(6.dp))
                Text("Debug版は署名が異なるため、正式版に上書き更新できません。初回のみDebug版を削除し、GitHub Releasesから正式版をインストールしてください。",
                    color = Purple, style = MaterialTheme.typography.bodySmall)
            }
            state.release?.let { release ->
                Text("新版: " + release.tag, color = Color.White, fontWeight = FontWeight.Bold)
                if (release.notes.isNotBlank()) {
                    Text(release.notes, color = Muted, maxLines = 5,
                        overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                if (state.updateDownloading) {
                    LinearProgressIndicator(progress = { state.updateProgress / 100f },
                        modifier = Modifier.fillMaxWidth())
                    Text(state.updateProgress.toString() + " %", color = Muted)
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.updateDownloading && !BuildConfig.DEBUG,
                    onClick = if (state.apkPath != null) vm::installUpdate else vm::downloadUpdate
                ) {
                    Text(if (state.apkPath != null) "確認して上書きインストール"
                        else "更新版APKを取得・検証")
                }
            } ?: Text("新しいリリースはありません", color = Muted)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.updateChecking,
                onClick = vm::checkUpdate
            ) { Text(if (state.updateChecking) "確認中…" else "アップデートを確認") }
            Text("APKのSHA256を確認し、最終的な署名検証はAndroidが行います。",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(16.dp))
    }
}
