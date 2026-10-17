package com.tans.tfiletransporter.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/**
 * لعبة تخبئة الأسلحة والمدافع (Cannon Arsenal Warfare / Battleship)
 * كل لاعب يخبئ مدافعه وأسلحته على رقعة الميدان سراً،
 * ثم يتناوب اللاعبان على إطلاق قذائف المدافع لقصف وتدمير أسلحة الخصم!
 * تدعم:
 * 1. اللعب الجماعي المباشر عبر شبكة شير لود (WiFi / Hotspot Multiplayer)
 * 2. اللعب الفردي ضد مدفع الروبوت الذكي (Solo vs Smart AI)
 * 3. اللعب الزوجي على نفس الجهاز (Pass & Play)
 */

enum class CannonWarMode { MULTIPLAYER, VS_AI, PASS_AND_PLAY }
enum class CannonGamePhase { PLACING, OPPONENT_PLACING, BATTLE, GAME_OVER }
enum class CellState { EMPTY, WEAPON, HIT, MISS }

data class CannonWeapon(
    val id: String,
    val nameAr: String,
    val size: Int,
    val icon: String
)

val DEFAULT_WEAPONS = listOf(
    CannonWeapon("heavy_cannon", "مدفع الهاون الثقيل", 3, "🏰"),
    CannonWeapon("rocket_launcher", "راجمة الصواريخ", 2, "🚀"),
    CannonWeapon("twin_cannon", "مدفع الميدان الثنائي", 2, "💥"),
    CannonWeapon("sniper_cannon", "مدفع القناص الخفي", 1, "🎯"),
    CannonWeapon("bomb_bunker", "مستودع الذخيرة والمدافع", 1, "💣")
)

const val GRID_SIZE = 6

@Composable
fun CannonWarGame(eventFlow: Flow<String>, sendEvent: (String) -> Unit) {
    var mode by remember { mutableStateOf(CannonWarMode.MULTIPLAYER) }
    var phase by remember { mutableStateOf(CannonGamePhase.PLACING) }
    
    // My Grid (Grid size x Grid size)
    var myGrid by remember { mutableStateOf(List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }) }
    // Enemy Radar Grid (for shooting at opponent)
    var enemyRadar by remember { mutableStateOf(List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }) }
    
    // For AI or Pass&Play
    var enemyActualGrid by remember { mutableStateOf(List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }) }
    
    var isMyTurn by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("خبئ مدافعك وأسلحتك في الميدان!") }
    var winner by remember { mutableStateOf<String?>(null) } // "ME" or "OPPONENT"
    
    // Turn tracking for Pass & Play
    var activePassPlayer by remember { mutableStateOf(1) } // 1 or 2
    var passP1Grid by remember { mutableStateOf(List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }) }
    var passP2Grid by remember { mutableStateOf(List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }) }
    var passP1Radar by remember { mutableStateOf(List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }) }
    var passP2Radar by remember { mutableStateOf(List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }) }
    var passCoverActive by remember { mutableStateOf(false) }

    // Weapon counts
    val myRemainingWeapons = remember(myGrid) { myGrid.count { it == CellState.WEAPON } }
    val enemyRemainingWeapons = remember(enemyActualGrid, enemyRadar, mode) {
        if (mode == CannonWarMode.MULTIPLAYER) {
            // Count hits on radar
            val totalEnemyWeaponCells = 9
            val hitsOnEnemy = enemyRadar.count { it == CellState.HIT }
            (totalEnemyWeaponCells - hitsOnEnemy).coerceAtLeast(0)
        } else {
            enemyActualGrid.count { it == CellState.WEAPON }
        }
    }

    fun deployRandom(targetGrid: List<CellState>): List<CellState> {
        val grid = MutableList(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }
        for (w in DEFAULT_WEAPONS) {
            var placed = false
            var attempts = 0
            while (!placed && attempts < 100) {
                attempts++
                val horizontal = Random.nextBoolean()
                val r = if (horizontal) Random.nextInt(GRID_SIZE) else Random.nextInt(GRID_SIZE - w.size + 1)
                val c = if (horizontal) Random.nextInt(GRID_SIZE - w.size + 1) else Random.nextInt(GRID_SIZE)
                
                val indices = (0 until w.size).map { i ->
                    if (horizontal) r * GRID_SIZE + (c + i) else (r + i) * GRID_SIZE + c
                }
                
                if (indices.all { grid[it] == CellState.EMPTY }) {
                    indices.forEach { grid[it] = CellState.WEAPON }
                    placed = true
                }
            }
        }
        return grid
    }

    fun resetGame() {
        myGrid = List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }
        enemyRadar = List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }
        enemyActualGrid = List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }
        phase = CannonGamePhase.PLACING
        isMyTurn = true
        winner = null
        statusMessage = "خبئ مدافعك وأسلحتك في الميدان!"
        activePassPlayer = 1
        passCoverActive = false
    }

    // AI Turn Logic
    suspend fun executeAiTurn() {
        delay(800)
        val availableIndices = myGrid.indices.filter { myGrid[it] == CellState.EMPTY || myGrid[it] == CellState.WEAPON }
        if (availableIndices.isEmpty()) return
        
        // Smart AI: if previous hit has adjacent empty/weapon, prioritize
        val hitIndices = myGrid.indices.filter { myGrid[it] == CellState.HIT }
        var targetIndex = -1
        for (hit in hitIndices) {
            val r = hit / GRID_SIZE
            val c = hit % GRID_SIZE
            val neighbors = listOf(
                (r - 1) * GRID_SIZE + c,
                (r + 1) * GRID_SIZE + c,
                r * GRID_SIZE + (c - 1),
                r * GRID_SIZE + (c + 1)
            ).filter { idx ->
                val nr = idx / GRID_SIZE
                val nc = idx % GRID_SIZE
                nr in 0 until GRID_SIZE && nc in 0 until GRID_SIZE && (myGrid[idx] == CellState.EMPTY || myGrid[idx] == CellState.WEAPON)
            }
            if (neighbors.isNotEmpty()) {
                targetIndex = neighbors.random()
                break
            }
        }
        if (targetIndex == -1) {
            targetIndex = availableIndices.random()
        }

        val current = myGrid[targetIndex]
        val newGrid = myGrid.toMutableList()
        if (current == CellState.WEAPON) {
            newGrid[targetIndex] = CellState.HIT
            statusMessage = "💥 قصف مدفع الروبوت أحد أسلحتك بنجاح!"
        } else {
            newGrid[targetIndex] = CellState.MISS
            statusMessage = "🌊 طاشت قذيفة مدفع الروبوت في الفراغ! دورك الآن."
        }
        myGrid = newGrid

        // Check if player lost
        if (myGrid.none { it == CellState.WEAPON }) {
            phase = CannonGamePhase.GAME_OVER
            winner = "OPPONENT"
            statusMessage = "💀 دمر مدفع الروبوت جميع أسلحتك! هزيمة."
        } else {
            isMyTurn = true
        }
    }

    // Handle incoming multiplayer network events
    LaunchedEffect(mode) {
        if (mode == CannonWarMode.MULTIPLAYER) {
            eventFlow.collect { event ->
                if (event.startsWith("CW:")) {
                    val payload = event.removePrefix("CW:")
                    when {
                        payload.startsWith("READY:") -> {
                            val weaponIndicesStr = payload.removePrefix("READY:")
                            val weaponIndices = weaponIndicesStr.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
                            val newActual = MutableList(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }
                            weaponIndices.forEach { if (it in newActual.indices) newActual[it] = CellState.WEAPON }
                            enemyActualGrid = newActual

                            if (phase == CannonGamePhase.OPPONENT_PLACING) {
                                phase = CannonGamePhase.BATTLE
                                isMyTurn = true
                                statusMessage = "⚔️ بدأت المعركة! أطلق مدفعك على رادار الخصم!"
                            }
                        }
                        payload.startsWith("FIRE:") -> {
                            val targetIndex = payload.removePrefix("FIRE:").toInt()
                            val isHit = myGrid[targetIndex] == CellState.WEAPON
                            val newMyGrid = myGrid.toMutableList()
                            newMyGrid[targetIndex] = if (isHit) CellState.HIT else CellState.MISS
                            myGrid = newMyGrid

                            sendEvent("CW:RESULT:$targetIndex:${if (isHit) "HIT" else "MISS"}")

                            if (myGrid.none { it == CellState.WEAPON }) {
                                phase = CannonGamePhase.GAME_OVER
                                winner = "OPPONENT"
                                sendEvent("CW:GAMEOVER:OPPONENT_WON")
                                statusMessage = "💀 دمر الخصم ترسانة أسلحتك ومدافعك! هزيمة."
                            } else {
                                isMyTurn = true
                                statusMessage = if (isHit) "💥 قصف مدفع الخصم أحد أسلحتك! دورك الآن للرد." else "🛡️ طاشت قذيفة الخصم! دورك الآن لإطلاق المدفع."
                            }
                        }
                        payload.startsWith("RESULT:") -> {
                            val parts = payload.removePrefix("RESULT:").split(":")
                            val targetIndex = parts[0].toInt()
                            val result = parts[1]
                            val newRadar = enemyRadar.toMutableList()
                            newRadar[targetIndex] = if (result == "HIT") CellState.HIT else CellState.MISS
                            enemyRadar = newRadar

                            val hits = enemyRadar.count { it == CellState.HIT }
                            if (hits >= 9) {
                                phase = CannonGamePhase.GAME_OVER
                                winner = "ME"
                                statusMessage = "🏆 انتصار كاسح! دمرت جميع مدافع وأسلحة الخصم!"
                            } else {
                                statusMessage = if (result == "HIT") "🎯 أصاب مدفعك سلاح الخصم بنجاح! بانتظار قذيفة الخصم..." else "💨 طاشت قذيفتك في الفراغ. بانتظار قذيفة الخصم..."
                                isMyTurn = false
                            }
                        }
                        payload == "RESET" -> {
                            resetGame()
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "💣 حرب المدافع والأسلحة",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "لعبة تخبئة الأسلحة والقصف التكتيكي",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
            
            // Mode Selector
            AssistChip(
                onClick = {
                    mode = when (mode) {
                        CannonWarMode.MULTIPLAYER -> CannonWarMode.VS_AI
                        CannonWarMode.VS_AI -> CannonWarMode.PASS_AND_PLAY
                        CannonWarMode.PASS_AND_PLAY -> CannonWarMode.MULTIPLAYER
                    }
                    resetGame()
                },
                label = {
                    Text(
                        when (mode) {
                            CannonWarMode.MULTIPLAYER -> "📡 شبكة شير لود"
                            CannonWarMode.VS_AI -> "🤖 ضد الروبوت"
                            CannonWarMode.PASS_AND_PLAY -> "👥 جهاز واحد"
                        },
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = statusMessage,
                    color = when {
                        winner == "ME" -> Color(0xFF4ADE80)
                        winner == "OPPONENT" -> Color(0xFFF87171)
                        isMyTurn -> Color(0xFF38BDF8)
                        else -> Color(0xFFE2E8F0)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (phase == CannonGamePhase.BATTLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(
                            text = "🛡️ أسلحتك المتبقية: $myRemainingWeapons",
                            color = Color(0xFF67E8F9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "🎯 أسلحة الخصم: $enemyRemainingWeapons",
                            color = Color(0xFFF472B6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 1. PLACEMENT PHASE
        if (phase == CannonGamePhase.PLACING || phase == CannonGamePhase.OPPONENT_PLACING) {
            Text(
                text = "📍 ساحة تخبئة أسلحتك (انقر لتوزيع أو إخفاء السلاح)",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            TacticalGrid(
                grid = myGrid,
                isInteractive = phase == CannonGamePhase.PLACING,
                onCellClick = { index ->
                    if (phase == CannonGamePhase.PLACING) {
                        val newGrid = myGrid.toMutableList()
                        newGrid[index] = if (newGrid[index] == CellState.WEAPON) CellState.EMPTY else CellState.WEAPON
                        myGrid = newGrid
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { myGrid = deployRandom(myGrid) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("🎲 تخبئة عشوائية", fontSize = 12.sp)
                }

                Button(
                    onClick = { myGrid = List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY } },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("🔄 مسح الرقعة", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentWeaponCount = myGrid.count { it == CellState.WEAPON }
            Button(
                onClick = {
                    if (currentWeaponCount < 5) {
                        statusMessage = "⚠️ يرجى تخبئة 5 خلايا أسلحة على الأقل في الميدان!"
                        return@Button
                    }
                    if (mode == CannonWarMode.VS_AI) {
                        enemyActualGrid = deployRandom(enemyActualGrid)
                        phase = CannonGamePhase.BATTLE
                        isMyTurn = true
                        statusMessage = "⚔️ بدأت المعركة ضد مدفع الروبوت! أطلق قذيفتك الأولى!"
                    } else if (mode == CannonWarMode.MULTIPLAYER) {
                        val weaponIndices = myGrid.indices.filter { myGrid[it] == CellState.WEAPON }.joinToString(",")
                        sendEvent("CW:READY:$weaponIndices")
                        if (enemyActualGrid.any { it == CellState.WEAPON }) {
                            phase = CannonGamePhase.BATTLE
                            isMyTurn = true
                            statusMessage = "⚔️ كِلا الطرفين جاهز! أطلق مدفعك على الخصم!"
                        } else {
                            phase = CannonGamePhase.OPPONENT_PLACING
                            statusMessage = "⏳ تم تجهيز مدافعك! بانتظار انتهاء الخصم من التخبئة..."
                        }
                    } else if (mode == CannonWarMode.PASS_AND_PLAY) {
                        passP1Grid = myGrid
                        passCoverActive = true
                        phase = CannonGamePhase.OPPONENT_PLACING
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                enabled = phase == CannonGamePhase.PLACING && currentWeaponCount >= 5
            ) {
                Text(
                    text = "⚔️ جاهز للحرب وتثبيت الأسلحة ($currentWeaponCount خلايا)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // 2. PASS & PLAY INTERMEDIATE COVER
        if (mode == CannonWarMode.PASS_AND_PLAY && passCoverActive) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    Button(onClick = {
                        passCoverActive = false
                        if (phase == CannonGamePhase.OPPONENT_PLACING) {
                            myGrid = List(GRID_SIZE * GRID_SIZE) { CellState.EMPTY }
                            phase = CannonGamePhase.PLACING
                            activePassPlayer = 2
                            statusMessage = "اللاعب 2: خبئ مدافعك وأسلحتك سراً!"
                        }
                    }) {
                        Text("متابعة")
                    }
                },
                title = { Text("مرر الجهاز للاعب 2") },
                text = { Text("أعطِ الهاتف للاعب الثاني ليخبئ مدافعك وأسلحته دون رؤية موقعك!") }
            )
        }

        // 3. BATTLE PHASE
        if (phase == CannonGamePhase.BATTLE) {
            // Radar Grid (Target Enemy)
            Text(
                text = "🎯 رادار العدو (انقر لتوجيه وإطلاق قذيفة المدفع)",
                color = Color(0xFFF87171),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            TacticalRadarGrid(
                radar = enemyRadar,
                isMyTurn = isMyTurn,
                onFire = { index ->
                    if (isMyTurn && enemyRadar[index] == CellState.EMPTY) {
                        if (mode == CannonWarMode.VS_AI) {
                            val isHit = enemyActualGrid[index] == CellState.WEAPON
                            val newRadar = enemyRadar.toMutableList()
                            newRadar[index] = if (isHit) CellState.HIT else CellState.MISS
                            enemyRadar = newRadar

                            val newActual = enemyActualGrid.toMutableList()
                            if (isHit) newActual[index] = CellState.HIT
                            enemyActualGrid = newActual

                            if (enemyActualGrid.none { it == CellState.WEAPON }) {
                                phase = CannonGamePhase.GAME_OVER
                                winner = "ME"
                                statusMessage = "🏆 انتصار كاسح! دمرت مدفع وترسانة الروبوت!"
                            } else {
                                statusMessage = if (isHit) "💥 أصاب مدفعك سلاحاً! انتظر رد الروبوت..." else "💨 طاشت قذيفتك! انتظر رد الروبوت..."
                                isMyTurn = false
                            }
                        } else if (mode == CannonWarMode.MULTIPLAYER) {
                            sendEvent("CW:FIRE:$index")
                            statusMessage = "🚀 القذيفة في طريقها لرادار الخصم..."
                            isMyTurn = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // My Battlefield (Showing Damage Taken)
            Text(
                text = "🛡️ ميدانك وترسانتك (موقع أسلحتك والقذائف الواردة)",
                color = Color(0xFF38BDF8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            TacticalGrid(
                grid = myGrid,
                isInteractive = false,
                onCellClick = {}
            )
        }

        // 4. GAME OVER PHASE
        if (phase == CannonGamePhase.GAME_OVER) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (winner == "ME") Color(0xFF14532D) else Color(0xFF7F1D1D)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (winner == "ME") "🏆 انتصار مدوي!" else "💀 دُمرت ترسانتك!",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (winner == "ME") "أصابت قذائفك جميع مخابئ ومدافع الخصم ودمرتها بالكامل!" else "تمكن الخصم من تحديد وتدمير مدافعك في المعركة.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (mode == CannonWarMode.MULTIPLAYER) {
                                sendEvent("CW:RESET")
                            }
                            resetGame()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = "🔄 معركة جديدة (إعادة التخبئة)",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Trigger AI turn when it's AI's turn
    LaunchedEffect(isMyTurn, phase, mode) {
        if (mode == CannonWarMode.VS_AI && !isMyTurn && phase == CannonGamePhase.BATTLE) {
            executeAiTurn()
        }
    }
}

@Composable
fun TacticalGrid(
    grid: List<CellState>,
    isInteractive: Boolean,
    onCellClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .padding(4.dp)
    ) {
        for (r in 0 until GRID_SIZE) {
            Row {
                for (c in 0 until GRID_SIZE) {
                    val index = r * GRID_SIZE + c
                    val state = grid[index]
                    val cellColor = when (state) {
                        CellState.WEAPON -> Color(0xFF0D9488)
                        CellState.HIT -> Color(0xFFEF4444)
                        CellState.MISS -> Color(0xFF475569)
                        CellState.EMPTY -> Color(0xFF0F172A)
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellColor)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .clickable(enabled = isInteractive) { onCellClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        when (state) {
                            CellState.WEAPON -> Text("💣", fontSize = 16.sp)
                            CellState.HIT -> Text("💥", fontSize = 18.sp)
                            CellState.MISS -> Text("🌊", fontSize = 14.sp)
                            CellState.EMPTY -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalRadarGrid(
    radar: List<CellState>,
    isMyTurn: Boolean,
    onFire: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1B4B))
            .padding(4.dp)
            .border(2.dp, if (isMyTurn) Color(0xFFEF4444) else Color(0xFF4338CA), RoundedCornerShape(8.dp))
    ) {
        for (r in 0 until GRID_SIZE) {
            Row {
                for (c in 0 until GRID_SIZE) {
                    val index = r * GRID_SIZE + c
                    val state = radar[index]
                    val cellColor = when (state) {
                        CellState.HIT -> Color(0xFFDC2626)
                        CellState.MISS -> Color(0xFF334155)
                        else -> Color(0xFF1E293B)
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellColor)
                            .border(1.dp, Color(0xFF4338CA), RoundedCornerShape(6.dp))
                            .clickable(enabled = isMyTurn && state == CellState.EMPTY) {
                                onFire(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (state) {
                            CellState.HIT -> Text("💥", fontSize = 18.sp)
                            CellState.MISS -> Text("⭕", fontSize = 14.sp, color = Color(0xFF94A3B8))
                            CellState.EMPTY -> {
                                if (isMyTurn) {
                                    Text("🎯", fontSize = 12.sp, color = Color(0x33FFFFFF))
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
