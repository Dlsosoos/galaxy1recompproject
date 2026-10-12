package com.pedro.galaxy1recomp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App(store) } } }
    }
}

fun displayName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: (uri.lastPathSegment ?: "arquivo")

@Composable
fun App(store: Store) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("▶" to "Jogar", "🧩" to "Mods", "⚙" to "Config", "⭐" to "Cheats")
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { i, (ic, l) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(ic) }, label = { Text(l) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> PlayTab(store)
                1 -> ModsTab(store)
                2 -> SettingsTab(store)
                else -> CheatsTab(store)
            }
        }
    }
}

@Composable
fun PlayTab(store: Store) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(store.getStr("rom_name", "")) }
    var uriStr by remember { mutableStateOf(store.getStr("rom_uri", "")) }
    var info by remember { mutableStateOf<RomInfo?>(null) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(uriStr) {
        if (uriStr.isNotEmpty()) info = withContext(Dispatchers.IO) { RomValidator.inspect(ctx, Uri.parse(uriStr)) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = displayName(ctx, uri)
            val l = n.lowercase()
            if (l.endsWith(".iso") || l.endsWith(".wbfs")) {
                try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                store.putStr("rom_uri", uri.toString()); store.putStr("rom_name", n)
                name = n; uriStr = uri.toString(); status = ""
            } else status = "Formato não suportado. Use .iso ou .wbfs"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🪐 Galaxy1Recomp", style = MaterialTheme.typography.headlineMedium)
        Text("Super Mario Galaxy 1 — recompilação para Android", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Escolher ROM (.iso / .wbfs)") }
        if (name.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    val i = info
                    if (i == null) Text("Verificando…") else {
                        Text(i.msg)
                        if (i.id.isNotEmpty()) Text("ID: ${i.id}  •  formato: ${i.format.uppercase()}")
                        if (i.region.isNotEmpty()) Text("Região: ${i.region}")
                    }
                }
            }
        }
        Button(
            enabled = info?.ok == true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val pfd = try { ctx.contentResolver.openFileDescriptor(Uri.parse(uriStr), "r") } catch (_: Exception) { null }
                if (pfd == null) status = "Não foi possível abrir a ROM"
                else if (!NativeBridge.loaded) { pfd.close(); status = "Biblioteca nativa não carregada" }
                else {
                    val mods = store.all().filter { it.key.startsWith("mod_") && it.value == true }
                        .keys.joinToString(",") { it.removePrefix("mod_") }
                    val r = NativeBridge.nativeStart(pfd.detachFd(), info!!.format, Settings.buildConfig(store), mods, Cheats.build(store))
                    status = if (r == 0) "Rodando" else "Núcleo recompilado ainda não vinculado (código $r). A interface, a leitura da ROM, os mods, as configurações e os cheats já estão prontos."
                }
            }
        ) { Text("▶ Jogar") }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun ModsTab(store: Store) {
    val ctx = LocalContext.current
    val dir = remember { File(ctx.filesDir, "mods").apply { mkdirs() } }
    var tick by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = displayName(ctx, uri)
            ctx.contentResolver.openInputStream(uri)?.use { i -> File(dir, n).outputStream().use { o -> i.copyTo(o) } }
            tick++
        }
    }
    val files = remember(tick) { dir.listFiles()?.sortedBy { it.name } ?: emptyList() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mods", style = MaterialTheme.typography.headlineSmall)
        Text("Importe pacotes de texturas, músicas ou arquivos de substituição (.zip, .szs, .brstm…).", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("➕ Importar mod") }
        if (files.isEmpty()) Text("Nenhum mod instalado.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(files, key = { it.name }) { f ->
                var on by remember(tick) { mutableStateOf(store.getBool("mod_" + f.name, true)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { on = it; store.putBool("mod_" + f.name, it) })
                    TextButton(onClick = { f.delete(); store.removeByPrefix("mod_" + f.name); tick++ }) { Text("🗑") }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(store: Store) {
    var tick by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Settings.sections.forEach { (sec, opts) ->
            item(key = "h_$sec") {
                Text(sec, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
            }
            items(opts, key = { it.key }) { o -> OptRow(store, o, tick) }
        }
        item(key = "reset") {
            OutlinedButton(onClick = { Settings.reset(store); tick++ }, modifier = Modifier.padding(vertical = 16.dp)) { Text("Restaurar padrões") }
        }
    }
}

private fun fmt(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

@Composable
fun OptRow(store: Store, o: Opt, tick: Int) {
    when (o) {
        is Sw -> {
            var v by remember(tick) { mutableStateOf(store.getBool(o.key, o.def)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(o.title, Modifier.weight(1f))
                Switch(checked = v, onCheckedChange = { v = it; store.putBool(o.key, it) })
            }
        }
        is Sl -> {
            var v by remember(tick) { mutableFloatStateOf(store.getFloat(o.key, o.def)) }
            Column {
                Text("${o.title}: ${fmt(v)}${o.unit}")
                Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { store.putFloat(o.key, v) },
                    valueRange = o.min..o.max, steps = o.steps)
            }
        }
        is Ch -> {
            var v by remember(tick) { mutableIntStateOf(store.getInt(o.key, o.def)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(o.title, Modifier.weight(1f))
                OutlinedButton(onClick = { v = (v + 1) % o.items.size; store.putInt(o.key, v) }) { Text(o.items[v]) }
            }
        }
    }
}

@Composable
fun CheatsTab(store: Store) {
    var tick by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Cheats", style = MaterialTheme.typography.headlineSmall) }
        item {
            var on by remember(tick) { mutableStateOf(store.getBool("cheat_" + Cheats.unlockAll.id, false)) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🌟 " + Cheats.unlockAll.title, style = MaterialTheme.typography.titleSmall)
                        Text(Cheats.unlockAll.desc, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = on, onCheckedChange = { on = it; store.putBool("cheat_" + Cheats.unlockAll.id, it) })
                }
            }
        }
        items(Cheats.list, key = { it.id }) { c ->
            var on by remember(tick) { mutableStateOf(store.getBool("cheat_" + c.id, false)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.title)
                    Text(c.desc, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = on, onCheckedChange = { on = it; store.putBool("cheat_" + c.id, it) })
            }
        }
        item {
            OutlinedButton(onClick = { store.removeByPrefix("cheat_"); tick++ }) { Text("Desativar todos") }
        }
    }
}
