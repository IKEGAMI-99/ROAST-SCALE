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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.ikegami.roastscale.data.Measurement
import jp.ikegami.roastscale.data.Profile
import jp.ikegami.roastscale.data.ReferenceGuide
import jp.ikegami.roastscale.data.ReferenceBand
import jp.ikegami.roastscale.data.MetricCatalog
import jp.ikegami.roastscale.data.HealthTrends
import jp.ikegami.roastscale.data.WaistEntry
import jp.ikegami.roastscale.ui.HealthExtraCharts
import jp.ikegami.roastscale.ui.MetricMeter
import jp.ikegami.roastscale.ui.HistoryCharts
import jp.ikegami.roastscale.ui.MeasurementCelebrationOverlay
import jp.ikegami.roastscale.ui.SlotWeightDisplay
import jp.ikegami.roastscale.ui.NeonBackground
import jp.ikegami.roastscale.ui.NeonPanel
import jp.ikegami.roastscale.ui.NeonDivider
import jp.ikegami.roastscale.ui.NeonSectionHeading
import jp.ikegami.roastscale.ui.RoastNeon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val Night = RoastNeon.night
private val Surface = RoastNeon.surface
private val Accent = RoastNeon.lime
private val Muted = RoastNeon.muted
private val Purple = RoastNeon.violet
private val Cyan = RoastNeon.cyan
private val Pink = RoastNeon.pink

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
    // Reception is based on actual BLE scale frames, not merely on scanning.
    // Cancel the 4-second grace period each time a fresh scale frame arrives.
    val lastFrameAt = state.bleDiagnostics.lastScaleFrameAtElapsed
    var receiving by remember { mutableStateOf(false) }
    LaunchedEffect(state.scanning, lastFrameAt) {
        if (!state.scanning) {
            receiving = false
            return@LaunchedEffect
        }
        val ageMs = android.os.SystemClock.elapsedRealtime() - lastFrameAt
        if (lastFrameAt > 0L && ageMs in 0L until 4_000L) {
            receiving = true
            delay(4_000L - ageMs)
        }
        receiving = false
    }
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
        primary = Cyan, onPrimary = Night,
        background = Night, surface = Surface,
        onSurface = Color.White, secondary = Purple,
        tertiary = Pink, onBackground = Color.White
    )
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Night)) {
        NeonBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xF20C142C),
                    tonalElevation = 0.dp
                ) {
                    listOf("⚡" to "測定", "▥" to "履歴", "⚙" to "設定").forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = { page = index },
                            icon = {
                                Text(
                                    if (page == index) "◆ " + item.first else item.first,
                                    color = if (page == index) Cyan else Muted,
                                    fontWeight = FontWeight.Black
                                )
                            },
                            label = {
                                Text(
                                    item.second,
                                    fontWeight = if (page == index) FontWeight.Black
                                    else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan, selectedTextColor = Cyan,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                                indicatorColor = Color(0xFF243557)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                state.message?.let { message ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color(0xEE243057))
                            .border(1.dp, Purple.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, Modifier.weight(1f).padding(vertical = 10.dp), color = Color.White)
                        TextButton(onClick = vm::clearMessage) { Text("閉じる") }
                    }
                }
                when (page) {
                    0 -> Dashboard(state, requestScan, vm::stopScan, vm, receiving)
                    1 -> HistoryScreen(state.measurements, state.waists, state.profile, vm::deleteMeasurement, vm::deleteWaist)
                    else -> SettingsScreen(state, vm)
                }
            }
        }
        // Particle effects span the entire screen, including the navigation bar.
        // Canvas consumes no taps and disappears when data stops arriving.
        MeasurementCelebrationOverlay(
            active = page == 0 && state.scanning && receiving,
            modifier = Modifier.fillMaxSize()
        )
        }
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String) {
    NeonSectionHeading(
        index = if (title == "設定") "03 / CONFIG" else "02 / ARCHIVE",
        title = title,
        subtitle = subtitle
    )
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = Cyan,
    content: @Composable ColumnScope.() -> Unit
) {
    NeonPanel(modifier = modifier, accent = accent, content = content)
}

private fun format(value: Double?, suffix: String = ""): String =
    if (value == null) "—" else String.format(Locale.JAPAN, "%.1f", value) + suffix

private fun date(millis: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN).format(Date(millis))

/** The large pulse appears only when data actually arrives from the scale. */
@Composable
private fun BleReceivingIndicator(receiving: Boolean, weightKg: Double?) {
    val pulse by rememberInfiniteTransition(label = "bleReceive").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "receivePulse"
    )
    val tone = if (receiving) Accent else Muted
    Panel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (receiving) "● 体組成計からデータ受信中" else "体組成計のデータを待機中",
                color = tone, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(136.dp).graphicsLayer {
                        scaleX = if (receiving) pulse else 1f
                        scaleY = if (receiving) pulse else 1f
                    }.background(tone.copy(alpha = 0.12f), CircleShape)
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(132.dp),
                    color = tone,
                    trackColor = tone.copy(alpha = 0.16f),
                    strokeWidth = 7.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (receiving) "LIVE" else "BLE",
                        color = Color.White, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    if (receiving && weightKg != null) {
                        Text("● LIVE DATA", color = tone,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (receiving) "測定値を受信しています。安定したら自動保存します"
                else "体組成計に乗って、測定が終わるまでお待ちください",
                color = Muted, style = MaterialTheme.typography.bodySmall
            )
            Text("保存後30秒間は重複登録を防止します",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Dashboard(
    state: RoastUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    vm: RoastViewModel,
    receiving: Boolean
) {
    val context = LocalContext.current
    val recent = state.measurements.firstOrNull()
    val previous = state.measurements.getOrNull(1)
    val delta = if (recent != null && previous != null) recent.weightKg - previous.weightKg else null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "R / PERSONAL HEALTH HUD  //  01",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text("ROAST SCALE",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (receiving) "● LIVE SIGNAL RECEIVED"
                    else if (state.scanning) "◉ SEARCHING FOR SCALE..."
                    else "YOUR DATA. YOUR LEVEL UP.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (state.scanning) Accent else Purple
                )
            }
            FilledIconButton(
                onClick = if (state.scanning) onStop else onStart,
                modifier = Modifier.size(60.dp)
                    .border(
                        2.dp,
                        Brush.linearGradient(listOf(Cyan, Purple, Pink)),
                        CircleShape
                    ),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (state.scanning) Purple else Accent,
                    contentColor = Night
                )
            ) {
                Text(if (state.scanning) "停止" else "BLE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black)
            }
        }
        if (state.scanning || state.status.startsWith("スキャンエラー") ||
            state.status.startsWith("BLE受信ゼロ")) {
            Text(state.status, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        // While packets are arriving, show the much larger slot readout first.
        // The idle BLE indicator would otherwise push the reels below the fold.
        if (state.scanning && !receiving) {
            BleReceivingIndicator(false, null)
        }
        Panel(accent = if (receiving) Pink else Cyan) {
            if (receiving) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✦ LIVE WEIGHT / 測定中",
                        style = MaterialTheme.typography.labelMedium, color = Cyan,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    SlotWeightDisplay(weightKg = state.bleDiagnostics.liveWeightKg)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (state.bleDiagnostics.liveWeightKg == null) "体重の数値を受信しています…"
                        else "小数第1位から順番にロックオン！",
                        color = Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("◆ CURRENT WEIGHT / 最新記録",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = Cyan)
                        Spacer(Modifier.height(10.dp))
                        Text(if (recent == null) "--.- kg" else format(recent.weightKg, " kg"),
                            style = MaterialTheme.typography.displayMedium,
                            color = Accent, fontWeight = FontWeight.Black)
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
            }
            Spacer(Modifier.height(12.dp))
            MetricMeter(MetricCatalog.all.first(), recent, state.measurements, state.profile, showTitle = false)
            Spacer(Modifier.height(12.dp))
            NeonDivider()
            Spacer(Modifier.height(10.dp))
            // Two compact meters per row. Weight above remains prominent.
            MetricCatalog.all.drop(1).chunked(2).forEachIndexed { rowIndex, pair ->
                if (rowIndex != 0) Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    pair.forEachIndexed { index, metric ->
                        NeonPanel(
                            modifier = Modifier.weight(1f),
                            accent = if ((rowIndex + index) % 3 == 0) Cyan
                                else if ((rowIndex + index) % 3 == 1) Purple else Pink
                        ) {
                            MetricMeter(
                                metric = metric,
                                current = recent,
                                history = state.measurements,
                                profile = state.profile,
                                compact = true
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("※BMI・体重以外は機器や推定式で差があります。目安は診断や減量指示ではありません。",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }

        Panel(accent = Purple) {
            Text("✦ BODY TREND  /  腹囲・直近7日間の平均",
                color = Cyan, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            val mean = HealthTrends.weight7DayAverage(state.measurements)
            val latestWaist = state.waists.firstOrNull()
            val ratio = latestWaist?.let { HealthTrends.waistToHeight(it.waistCm, state.profile.heightCm) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("7日平均体重", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(format(mean, " kg"), color = Color.White,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("直近7暦日 / 1日1件", color = Muted,
                        style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text("腹囲（手入力）", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(format(latestWaist?.waistCm, " cm"), color = Color.White,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("腹囲身長比 " + format(ratio), color = when {
                        ratio == null -> Muted
                        ratio < 0.5 -> Accent
                        else -> Purple
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("腹囲身長比は0.5未満が一般的な成人の目安です。",
                color = Muted, style = MaterialTheme.typography.labelSmall)
            var waistInput by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = waistInput,
                    onValueChange = { raw ->
                        waistInput = raw.filter { it.isDigit() || it == '.' }.take(6)
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("腹囲 cm") },
                    singleLine = true
                )
                Button(onClick = {
                    vm.addWaist(waistInput.toDoubleOrNull())
                    if ((waistInput.toDoubleOrNull() ?: 0.0) in 30.0..200.0) waistInput = ""
                }) { Text("記録") }
            }
        }

        Panel(accent = Pink) {
            Text("✦ INTELLIGENCE  /  ChatGPTにダイエット相談",
                color = RoastNeon.pink, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("すべての測定履歴と腹囲をCSVにまとめ、相談用プロンプトと一緒に共有します。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Button(onClick = vm::shareChatGPT, modifier = Modifier.fillMaxWidth()) {
                Text("ChatGPTに共有（文章とCSV）")
            }
            OutlinedButton(onClick = vm::shareCsv, modifier = Modifier.fillMaxWidth()) {
                Text("CSVファイルだけ共有")
            }
            Text("共有はボタン操作時のみ。相談文＋CSVをクリップボードにもコピーします。送信前に内容をご確認ください。",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HistoryScreen(
    history: List<Measurement>,
    waists: List<WaistEntry>,
    profile: Profile,
    onDelete: (Long) -> Unit,
    onDeleteWaist: (Long) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Measurement?>(null) }
    var pendingWaistDelete by remember { mutableStateOf<WaistEntry?>(null) }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("この記録を削除しますか？") },
            text = { Text(date(target.measuredAt) + " の " +
                format(target.weightKg, " kg") +
                " を削除します。測定値がグラフからも消えます。元には戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    pendingDelete = null
                }) { Text("削除する", color = Color(0xFFFF9C9C)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("キャンセル") }
            }
        )
    }
    pendingWaistDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingWaistDelete = null },
            title = { Text("腹囲記録を削除しますか？") },
            text = { Text(date(target.measuredAt) + " / " + format(target.waistCm," cm") +
                " の記録を削除します。元には戻せません。") },
            confirmButton = {
                TextButton(onClick = { onDeleteWaist(target.id); pendingWaistDelete = null }) {
                    Text("削除する", color = Color(0xFFFF9C9C))
                }
            },
            dismissButton = { TextButton(onClick = { pendingWaistDelete = null }) { Text("キャンセル") } }
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TitleBlock("測定履歴", "各項目の折れ線グラフと記録の管理")
        }
        item {
            HistoryCharts(history, profile)
            Spacer(Modifier.height(12.dp))
            HealthExtraCharts(history, waists, profile)
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("測定記録 · " + history.size + "件",
                color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge)
        }
        if (history.isEmpty()) {
            item { Panel { Text("まだ測定履歴がありません", color = Muted) } }
        }
        item {
            Text("腹囲記録 · " + waists.size + "件", color = Color.White,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
        items(waists, key = { "waist-" + it.id }) { w ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(date(w.measuredAt), color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(format(w.waistCm," cm"), color = Color.White,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("腹囲身長比: " + format(HealthTrends.waistToHeight(w.waistCm, profile.heightCm)),
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { pendingWaistDelete = w }) {
                        Text("削除", color = Color(0xFFFF9C9C))
                    }
                }
            }
        }
        items(history, key = { it.id }) { row ->
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(date(row.measuredAt), color = Muted,
                            style = MaterialTheme.typography.labelSmall)
                        Text(format(row.weightKg, " kg"), color = Color.White,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("体脂肪 " + format(row.composition?.fatPercent, " %") +
                            "  ·  BMI " + format(row.composition?.bmi), color = Muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { pendingDelete = row }) {
                        Text("削除", color = Color(0xFFFF9C9C))
                    }
                }

            }
        }
    }
}

@Composable
private fun SettingsScreen(state: RoastUiState, vm: RoastViewModel) {
    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importCsv) }
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
        TitleBlock("設定", "データ共有は操作した時のみ")
        Panel(accent = Cyan) {
            Text("◆ PROFILE  /  測定プロフィール", color = Cyan,
                fontWeight = FontWeight.Black)
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

        Panel(accent = Purple) {
            Text("◆ DATA VAULT  /  CSVバックアップの復元", color = Purple,
                fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("ROAST SCALEから書き出したCSVを読み込み、体重・体組成・腹囲の履歴を追加します。既存データは削除せず、同じ日時と値の記録は重複登録しません。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { csvPicker.launch(arrayOf("*/*")) }
            ) { Text("CSVファイルを選んでインポート") }
            Text("端末内のファイルだけを読み込みます（10MB・最大50,000件）。",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }

        Panel(accent = Pink) {
            Text("◆ SYSTEM UPDATE  /  アプリ更新",
                color = Pink, fontWeight = FontWeight.Black)
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
