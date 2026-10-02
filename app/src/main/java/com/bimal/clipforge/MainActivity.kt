package com.bimal.clipforge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import com.google.firebase.functions.FirebaseFunctions
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import android.widget.Toast
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val volume: Float = 1f,
    val speed: Float = 1f
)

data class Preset(
    val name: String,
    val ratio: String,
    val width: Int,
    val height: Int
)

private val presets = listOf(
    Preset("Shorts", "9:16", 1080, 1920),
    Preset("TikTok", "9:16", 1080, 1920),
    Preset("Reels", "9:16", 1080, 1920),
    Preset("FB Reels", "9:16", 1080, 1920),
    Preset("YouTube", "16:9", 1920, 1080),
    Preset("Feed", "4:5", 1080, 1350)
)

private val Bg = Color(0xFF080A12)
private val Panel = Color(0xFF151927)
private val Accent = Color(0xFF8B6CFF)
private val AccentBlue = Color(0xFF36A9FF)

@UnstableApi
private class ConstantSpeedProvider(speed: Float) : SpeedProvider {
    private val safeSpeed = speed.coerceIn(0.25f, 4f)

    override fun getSpeed(presentationTimeUs: Long): Float = safeSpeed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

@UnstableApi
class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg,
                    surface = Panel,
                    primary = Accent
                )
            ) {
                ClipForge()
            }
        }
    }

    @Composable
    private fun ClipForge() {
        var clips by remember { mutableStateOf(listOf<Clip>()) }
        var selectedIndex by remember { mutableIntStateOf(-1) }
        var preset by remember { mutableStateOf(presets.first()) }
        var projectName by remember { mutableStateOf("My Short") }
        var textOverlay by remember { mutableStateOf("") }
        var musicName by remember { mutableStateOf("") }
        var musicUri by remember { mutableStateOf<Uri?>(null) }
        var musicVolume by remember { mutableFloatStateOf(0.35f) }
        var stickerText by remember { mutableStateOf("✨") }
        var captionStyle by remember { mutableStateOf("Bold") }
        var transitionName by remember { mutableStateOf("Cut") }
        var showAi by remember { mutableStateOf(false) }
        var showTemplates by remember { mutableStateOf(false) }
        var aiStyle by remember { mutableStateOf("Viral Short") }
        var beatSync by remember { mutableStateOf(false) }
        var autoCaptions by remember { mutableStateOf(false) }
        var aiSummary by remember { mutableStateOf("") }
        var aiBusy by remember { mutableStateOf(false) }
        var aiResult by remember { mutableStateOf("") }
        var filterName by remember { mutableStateOf("None") }
        var exporting by remember { mutableStateOf(false) }
        var progress by remember { mutableFloatStateOf(0f) }
        var dialog by remember { mutableStateOf("") }
        var history by remember { mutableStateOf(listOf<List<Clip>>()) }
        var redoStack by remember { mutableStateOf(listOf<List<Clip>>()) }

        val audioPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                musicUri = uri
                musicName = "Imported music"
            }
        }

        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (uris.isNotEmpty()) {
                history = history + listOf(clips)
                redoStack = emptyList()

                val firstNumber = clips.size + 1
                val imported = uris.mapIndexedNotNull { index, uri ->
                    val duration = readDurationMs(uri)
                    if (duration <= 0L) return@mapIndexedNotNull null

                    Clip(
                        uri = uri,
                        name = "Clip ${firstNumber + index}",
                        startMs = 0L,
                        endMs = duration
                    )
                }

                clips = clips + imported
                if (selectedIndex < 0 && clips.isNotEmpty()) {
                    selectedIndex = 0
                }

                if (imported.size != uris.size) {
                    toast("Some files could not be read as videos.")
                }
            }
        }
        val selected=clips.getOrNull(selectedIndex)

        fun commit(next:List<Clip>){
            history=history+listOf(clips); redoStack=emptyList(); clips=next
        }
        fun undo(){
            val p=history.lastOrNull()?:return
            redoStack=redoStack+listOf(clips); clips=p; history=history.dropLast(1)
            selectedIndex=selectedIndex.coerceAtMost(clips.lastIndex).coerceAtLeast(-1)
        }
        fun redo(){
            val n=redoStack.lastOrNull()?:return
            history=history+listOf(clips); clips=n; redoStack=redoStack.dropLast(1)
            selectedIndex=selectedIndex.coerceAtMost(clips.lastIndex).coerceAtLeast(-1)
        }

        Box(Modifier.fillMaxSize()) {
            VirelixBackground()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Column{
                    Text("Virelix",style=MaterialTheme.typography.headlineSmall)
                    Text("AI-assisted Shorts & Reels editor",color=Color.LightGray)
                }
                Row{
                    IconButton(enabled=history.isNotEmpty(),onClick={undo()}){Text("↶")}
                    IconButton(enabled=redoStack.isNotEmpty(),onClick={redo()}){Text("↷")}
                    Button(onClick={onClick@{picker.launch("video/*")}}){Icon(Icons.Default.Add,null);Spacer(Modifier.width(3.dp));Text("Add")}
                }
            }
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(value=projectName,onValueChange={projectName=it},label={Text("Project name")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(7.dp))

            if(selected!=null) {
                VideoPreview(selected.uri,this@MainActivity)
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xCC151927)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.10f)
                    )
                ) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("✦", color = Accent, style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create your next short",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Turn your clips into polished Shorts, Reels and TikToks.",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { picker.launch("video/*") },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Choose Videos")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "AI editing • Music • Captions • Effects",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(7.dp))
            Text("Timeline",style=MaterialTheme.typography.titleMedium)
            TimelinePanel(clips,selectedIndex){selectedIndex=it}

            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                EditorTool("✂ Trim"){if(selected!=null)dialog="trim"}
                EditorTool("Split"){
                    selected?.let{
                        val mid=it.startMs+(it.endMs-it.startMs)/2
                        if(mid>it.startMs+300 && mid<it.endMs-300){
                            val a=it.copy(endMs=mid,name="${it.name}-A")
                            val b=it.copy(id=UUID.randomUUID().toString(),startMs=mid,name="${it.name}-B")
                            commit(clips.toMutableList().apply{removeAt(selectedIndex);add(selectedIndex,a);add(selectedIndex+1,b)})
                        }
                    }
                }
                EditorTool("Aa Text"){dialog="text"}
                EditorTool("🎵 Music"){dialog="music"}
                EditorTool("✨ Effects"){dialog="effects"}
                EditorTool("🤖 AI Edit"){showAi=true}
                EditorTool("🎞 Templates"){showTemplates=true}
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                EditorTool("Duplicate"){
                    selected?.let{commit(clips.toMutableList().apply{add(selectedIndex+1,it.copy(id=UUID.randomUUID().toString(),name="${it.name} Copy"))});selectedIndex++}
                }
                EditorTool("Mute"){
                    selected?.let{commit(clips.toMutableList().apply{this[selectedIndex]=it.copy(volume=if(it.volume>0f)0f else 1f)})}
                }
                EditorTool("Speed"){
                    selected?.let{
                        val s=when(it.speed){1f->1.5f;1.5f->2f;else->1f}
                        commit(clips.toMutableList().apply{this[selectedIndex]=it.copy(speed=s)})
                    }
                }
                EditorTool("Delete"){
                    if(selectedIndex>=0){commit(clips.filterIndexed{i,_->i!=selectedIndex});selectedIndex=(selectedIndex-1).coerceAtLeast(0)}
                }
            }
            Spacer(Modifier.height(7.dp))
            if(aiSummary.isNotBlank()) Text(aiSummary,color=Accent,style=MaterialTheme.typography.labelMedium)
            Text("Format",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                presets.forEach{FilterChip(selected=preset==it,onClick={preset=it},label={Text("${it.name} ${it.ratio}")})}
            }
            Spacer(Modifier.height(7.dp))
            Text(
                if (clips.isEmpty()) "Timeline is empty"
                else "${clips.size} clip(s) • ${clips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) } / 1000L}s timeline",
                color = Color.LightGray
            )
            Text(
                "Text: ${if(textOverlay.isBlank()) "off" else "on"} • Music: ${if(musicUri==null) "off" else "on"} • Filter: $filterName • Transition: $transitionName",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(4.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = clips.isNotEmpty() && !exporting,
                onClick = {
                    exporting = true
                    progress = 0f

                    exportTimeline(
                        clips = clips,
						preset = preset,
						textOverlay = textOverlay,
						musicUri = musicUri,
						onProgress = { value -> runOnUiThread { progress = value } },
                        onComplete = { file ->
                            runOnUiThread {
                                exporting = false
                                saveProject(
                                    projectName,
                                    preset,
                                    clips,
                                    file,
                                    textOverlay,
                                    musicName,
                                    filterName,
                                    musicUri,
                                    musicVolume,
                                    stickerText,
                                    captionStyle,
                                    transitionName,
                                    aiStyle,
                                    autoCaptions,
                                    beatSync
                                )
                                share(file)
                            }
                        },
                        onError = { message ->
                            runOnUiThread {
                                exporting = false
                                toast(message)
                            }
                        }
                    )
                }
            ) {
                Text("Export • ${preset.ratio}")
            }

            if(dialog=="trim"&&selected!=null){
                var s by remember(selected.id){mutableLongStateOf(selected.startMs)}
                var e by remember(selected.id){mutableLongStateOf(selected.endMs)}
                AlertDialog(onDismissRequest={dialog=""},confirmButton={Button(onClick={
                    commit(clips.toMutableList().apply{this[selectedIndex]=selected.copy(startMs=s,endMs=e)});dialog=""
                }){Text("Apply")}},dismissButton={TextButton(onClick={dialog=""}){Text("Cancel")}},title={Text("Trim clip")},text={
                    Column{Text("Start ${fmt(s)}");Slider(value=s.toFloat(),onValueChange={s=it.toLong().coerceAtMost(e-300)},valueRange=0f..e.toFloat())
                    Text("End ${fmt(e)}");Slider(value=e.toFloat(),onValueChange={e=it.toLong().coerceAtLeast(s+300)},valueRange=(s+300).toFloat()..selected.endMs.toFloat())}
                })
            }
            if(dialog=="text") AlertDialog(
                onDismissRequest={dialog=""},
                confirmButton={Button(onClick={dialog=""}){Text("Done")}},
                title={Text("Text, captions & sticker")},
                text={
                    Column {
                        OutlinedTextField(
                            value=textOverlay,
                            onValueChange={textOverlay=it},
                            label={Text("Caption text")}
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Caption style")
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                            listOf("Bold","Minimal","Neon","Subtitle").forEach{n->
                                FilterChip(selected=captionStyle==n,onClick={captionStyle=n},label={Text(n)})
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value=stickerText,
                            onValueChange={stickerText=it},
                            label={Text("Sticker / emoji")}
                        )
                    }
                }
            )
            if(dialog=="music") AlertDialog(
                onDismissRequest={dialog=""},
                confirmButton={Button(onClick={dialog=""}){Text("Done")}},
                title={Text("Music track")},
                text={
                    Column {
                        Text(if (musicUri == null) "No music selected" else musicName)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { audioPicker.launch("audio/*") }) { Text("Choose audio") }
                        Spacer(Modifier.height(6.dp))
                        Text("Volume ${(musicVolume*100).toInt()}%")
                        Slider(value=musicVolume,onValueChange={musicVolume=it})
                        Text("Use music you have permission to use.", color=Color.Gray)
                    }
                }
            )
            if(dialog=="effects") AlertDialog(
                onDismissRequest={dialog=""},
                confirmButton={Button(onClick={dialog=""}){Text("Apply")}},
                title={Text("Filters & transitions")},
                text={
                    Column {
                        Text("Filter", style=MaterialTheme.typography.titleMedium)
                        listOf("None","Clean","Warm","Cool","Cinematic","B&W").forEach{n->
                            Row(verticalAlignment=Alignment.CenterVertically){
                                RadioButton(selected=filterName==n,onClick={filterName=n})
                                Text(n)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Transition", style=MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                            listOf("Cut","Fade","Zoom","Slide").forEach{n->
                                FilterChip(selected=transitionName==n,onClick={transitionName=n},label={Text(n)})
                            }
                        }
                    }
                }
            )

            if(showAi) AlertDialog(
                onDismissRequest={showAi=false},
                confirmButton={
                    Button(enabled=!aiBusy,onClick={
                        if(clips.isEmpty()){
                            aiSummary="Add at least one clip first."
                            showAi=false
                        } else {
                            aiBusy=true
                            requestAiEditPlan(
                                clips, aiStyle, autoCaptions, beatSync,
                                onSuccess={summary->
                                    aiBusy=false
                                    aiSummary=summary
                                    aiResult=summary
                                    showAi=false
                                },
                                onError={err->
                                    aiBusy=false
                                    // Safe offline fallback keeps the editor usable.
                                    val target=when(aiStyle){
                                        "Fast Viral"->1800L
                                        "Cinematic"->3500L
                                        else->2500L
                                    }
                                    history=history+listOf(clips)
                                    clips=clips.map{c->
                                        val d=(c.endMs-c.startMs).coerceAtLeast(600L)
                                        c.copy(endMs=c.startMs+d.coerceAtMost(target))
                                    }
                                    if(autoCaptions&&textOverlay.isBlank()) textOverlay="Watch till the end! 🔥"
                                    aiSummary="Offline smart edit applied. AI backend: $err"
                                    showAi=false
                                }
                            )
                        }
                    }){Text(if(aiBusy) "Analyzing…" else "Create AI edit")}
                },
                dismissButton={TextButton(onClick={showAi=false}){Text("Cancel")}},
                title={Text("AI Auto-Edit")},
                text={
                    Column {
                        Text("Choose a style")
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                            listOf("Viral Short","Fast Viral","Cinematic").forEach{n->
                                FilterChip(selected=aiStyle==n,onClick={aiStyle=n},label={Text(n)})
                            }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=autoCaptions,onCheckedChange={autoCaptions=it})
                            Text("Auto-caption plan")
                        }
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=beatSync,onCheckedChange={beatSync=it})
                            Text("Beat-sync cuts")
                        }
                        Text("V1.7 uses a local smart-edit engine. Secure Gemini/OpenAI integration can be added through Firebase Cloud Functions.",
                            color=Color.Gray,style=MaterialTheme.typography.bodySmall)
                    }
                }
            )

            if(showTemplates) AlertDialog(
                onDismissRequest={showTemplates=false},
                confirmButton={Button(onClick={showTemplates=false}){Text("Use template")}},
                title={Text("Shorts Templates")},
                text={
                    Column {
                        listOf("🔥 Viral Hook","🎵 Beat Drop","✨ Cinematic Story","📚 Educational","✝️ Inspirational").forEach { t->
                            Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){
                                RadioButton(selected=aiStyle==t,onClick={aiStyle=t})
                                Text(t)
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(18.dp))
            Text(
                "VIRELIX  •  CREATE. EDIT. SHARE.",
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.28f),
                style = MaterialTheme.typography.labelSmall
            )

            if(exporting) AlertDialog(onDismissRequest={},confirmButton={},title={Text("Exporting")},text={Column{LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth());Text("${(progress*100).toInt()}%")}})
            }
        }
    }

    @Composable
    private fun VirelixBackground() {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF080A12),
                            Color(0xFF10152A),
                            Color(0xFF090B15)
                        )
                    )
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Accent.copy(alpha = 0.24f),
                            Color.Transparent
                        )
                    ),
                    radius = w * 0.70f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.08f)
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentBlue.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    ),
                    radius = w * 0.58f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.04f, h * 0.48f)
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.035f),
                            Color.Transparent
                        )
                    ),
                    radius = w * 0.75f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.95f)
                )
            }
        }
    }

    @Composable
    private fun TimelinePanel(clips:List<Clip>,selected:Int,onSelect:(Int)->Unit){
        Column(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(14.dp)).padding(7.dp)){
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                clips.forEachIndexed{i,c->
                    Card(onClick={onSelect(i)},colors=CardDefaults.cardColors(containerColor=if(i==selected)Accent else Color(0xFF252A36)),modifier=Modifier.width(98.dp).height(58.dp)){
                        Column(Modifier.padding(6.dp)){Text(c.name,maxLines=1);Text("${fmt(c.startMs)}-${fmt(c.endMs)}",style=MaterialTheme.typography.labelSmall);Text("${c.speed}x",style=MaterialTheme.typography.labelSmall)}
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("VIDEO  ━━━━━━━━━━━━━━━━━━━")
            Text("TEXT   ─── caption ───────────",color=Color.Gray)
            Text("AUDIO  ─── music ─────────────",color=Color.Gray)
            Text("FX     ─── effects ───────────",color=Color.Gray)
        }
    }

    @Composable
    private fun EditorTool(label: String, onClick: () -> Unit) {
        OutlinedButton(onClick = onClick) { Text(label) }
    }

    @Composable
    private fun VideoPreview(uri: Uri, context: Context) {
        val player = remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }

        DisposableEffect(player) {
            onDispose { player.release() }
        }

        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxWidth().height(320.dp)
        )
    }

    @UnstableApi
    private fun exportClip(
        clip: Clip,
        preset: Preset,
        onProgress: (Float) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        // V1.4: render the selected timeline clip with its trim, speed and volume.
        // Full multi-clip composition is implemented by exportTimeline below.
        val output = File(cacheDir, "clipforge_${UUID.randomUUID()}.mp4")

        val item = MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs)
                    .build()
            )
            .build()

        val presentation = Presentation.createForWidthAndHeight(
            preset.width, preset.height,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )

        val edited = EditedMediaItem.Builder(item)
            .setEffects(Effects(emptyList(), listOf(presentation)))
            .setFrameRate(30)
            .setRemoveAudio(clip.volume <= 0f)
            .setSpeed(ConstantSpeedProvider(clip.speed))
            .build()

        startTransformer(edited, output, onProgress, onComplete, onError)
    }


    @UnstableApi
    private fun buildTextOverlay(text: String): OverlayEffect? {
        if (text.isBlank()) return null

        val width = 1080
        val height = 280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 78f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 0f, 4f, android.graphics.Color.BLACK)
        }

        canvas.drawText(
            text.take(45),
            width / 2f,
            height * 0.65f,
            paint
        )

        val overlay = BitmapOverlay.createStaticBitmapOverlay(bitmap)
        return OverlayEffect(listOf(overlay))
    }

    @UnstableApi
    private fun exportTimeline(
        clips: List<Clip>,
		preset: Preset,
		textOverlay: String,
		musicUri: Uri?,
		onProgress: (Float) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (clips.isEmpty()) {
            onError("Add at least one video clip.")
            return
        }

        val output = File(cacheDir, "virelix_${UUID.randomUUID()}.mp4")
        val presentation = Presentation.createForWidthAndHeight(
            preset.width,
            preset.height,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )

        val videoEffects = buildList<Effect> {
            add(presentation)
            buildTextOverlay(textOverlay)?.let(::add)
        }

        val editedItems = clips.map { clip ->
            val safeEnd = clip.endMs.coerceAtLeast(clip.startMs + 300L)

            val mediaItem = MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                        .setEndPositionMs(safeEnd)
                        .build()
                )
                .build()

            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(clip.volume <= 0f)
                .setEffects(
                    Effects(
                        emptyList(),
                        videoEffects
                    )
                )
                .setFrameRate(30)
                .setSpeed(ConstantSpeedProvider(clip.speed))
                .build()
        }

        val videoSequence =
            EditedMediaItemSequence.withAudioAndVideoFrom(editedItems)

        val composition = if (musicUri != null) {
            val musicItem = EditedMediaItem.Builder(
                MediaItem.fromUri(musicUri!!)
            ).build()

            val musicSequence = EditedMediaItemSequence
                .withAudioFrom(listOf(musicItem))
                .buildUpon()
                .setIsLooping(true)
                .build()

            Composition.Builder(videoSequence, musicSequence).build()
        } else {
            Composition.Builder(videoSequence).build()
        }

        val transformer = Transformer.Builder(this)
            .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
            .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: androidx.media3.transformer.ExportResult
                ) {
                    onProgress(1f)
                    onComplete(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    onError(exportException.message ?: "Final render failed")
                }
            })
            .build()

        try {
            transformer.start(composition, output.absolutePath)
            watchProgress(transformer, onProgress)
        } catch (error: Exception) {
            onError(error.message ?: "Could not start export")
        }
    }

    @UnstableApi
    private fun startTransformer(
        edited: EditedMediaItem,
        output: File,
        onProgress: (Float) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val transformer = Transformer.Builder(this)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: androidx.media3.transformer.ExportResult
                ) {
                    onProgress(1f)
                    onComplete(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    onError(exportException.message ?: "Export failed")
                }
            })
            .build()

        transformer.start(edited, output.absolutePath)
        watchProgress(transformer, onProgress)
    }

    @UnstableApi
    private fun watchProgress(
        transformer: Transformer,
        onProgress: (Float) -> Unit
    ) {
        Thread {
            val holder = ProgressHolder()

            while (true) {
                val state = transformer.getProgress(holder)

                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress.coerceIn(0, 100) / 100f)
                }

                if (state == Transformer.PROGRESS_STATE_NOT_STARTED ||
                    holder.progress >= 100
                ) {
                    break
                }

                Thread.sleep(250)
            }
        }.start()
    }

    private fun readDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun fmt(ms:Long):String{
        val s=(ms/1000L).coerceAtLeast(0L)
        return "%02d:%02d".format(s/60L,s%60L)
    }


    private fun requestAiEditPlan(
        clips: List<Clip>,
        style: String,
        wantCaptions: Boolean,
        wantBeatSync: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val payload = hashMapOf(
            "style" to style,
            "autoCaptions" to wantCaptions,
            "beatSync" to wantBeatSync,
            "clips" to clips.map {
                hashMapOf(
                    "name" to it.name,
                    "startMs" to it.startMs,
                    "endMs" to it.endMs,
                    "durationMs" to (it.endMs - it.startMs).coerceAtLeast(0L)
                )
            }
        )

        FirebaseFunctions.getInstance()
            .getHttpsCallable("generateEditPlan")
            .call(payload)
            .addOnSuccessListener { result ->
                val data = result.data as? Map<*, *>
                val summary = data?.get("summary")?.toString()
                    ?: "AI edit plan received."
                onSuccess(summary)
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "AI request failed")
            }
    }

    private fun saveProject(
        name: String,
        preset: Preset,
        clips: List<Clip>,
        exported: File,
        text: String,
        music: String,
        filter: String,
        musicUri: Uri?,
        musicVolume: Float,
        sticker: String,
        captionStyle: String,
        transition: String,
        aiStyle: String = "Viral Short",
        autoCaptions: Boolean = false,
        beatSync: Boolean = false
    ) {
        val user = auth.currentUser ?: return
        val projectId = UUID.randomUUID().toString()
        val ref = storage.reference.child("users/${user.uid}/exports/$projectId.mp4")

        ref.putFile(Uri.fromFile(exported))
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: RuntimeException("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { url ->
                firestore.collection("projects").document(projectId).set(
                    mapOf(
                        "ownerId" to user.uid,
                        "name" to name,
                        "platform" to preset.name,
                        "aspectRatio" to preset.ratio,
                        "clipCount" to clips.size,
                        "textOverlay" to text,
                        "musicTrack" to music,
                        "musicUri" to (musicUri?.toString() ?: ""),
                        "musicVolume" to musicVolume,
                        "filter" to filter,
                        "sticker" to sticker,
                        "captionStyle" to captionStyle,
                        "transition" to transition,
                        "aiStyle" to aiStyle,
                        "autoCaptions" to autoCaptions,
                        "beatSync" to beatSync,
                        "timeline" to clips.map { mapOf("name" to it.name, "startMs" to it.startMs, "endMs" to it.endMs, "volume" to it.volume, "speed" to it.speed) },
                        "videoUrl" to url.toString(),
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
            }
    }

    private fun share(file: File) {
        val contentUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share video"))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
