package com.tans.tfiletransporter.ui.games

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tans.tfiletransporter.ui.filetransport.FileTransportActivity
import com.tans.tfiletransporter.transferproto.fileexplore.requestMsgSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tans.tfiletransporter.logs.AndroidLog

enum class GameType { NONE, TICTACTOE, CONNECT4, OTHELLO, CANNON_WAR }

@Composable
fun GamesScreen(activity: FileTransportActivity) {
    var currentGame by remember { mutableStateOf(GameType.NONE) }
    
    val sendGameEvent: (String) -> Unit = { jsonStr ->
        activity.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                activity.fileExplore.requestMsgSuspend("[GAME_EVENT]:$jsonStr")
            }.onFailure {
                AndroidLog.e("GamesScreen", "Send game event fail: $it", it)
            }
        }
    }
    
    // Listen for remote game start
    LaunchedEffect(Unit) {
        activity.gameEventFlow.collect { jsonStr ->
            if (jsonStr.startsWith("START_GAME:")) {
                val game = jsonStr.removePrefix("START_GAME:")
                when(game) {
                    "TICTACTOE" -> currentGame = GameType.TICTACTOE
                    "CONNECT4" -> currentGame = GameType.CONNECT4
                    "OTHELLO" -> currentGame = GameType.OTHELLO
                    "CANNON_WAR" -> currentGame = GameType.CANNON_WAR
                }
            } else if (jsonStr == "EXIT_GAME") {
                currentGame = GameType.NONE
            }
        }
    }

    if (currentGame == GameType.NONE) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("اختر لعبة للمنافسة! 🎮", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            // Featured New Game: Cannon Weapons War
            Button(
                onClick = { 
                    currentGame = GameType.CANNON_WAR
                    sendGameEvent("START_GAME:CANNON_WAR")
                },
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF0D9488))
            ) {
                Text("💣 حرب المدافع وتخبئة الأسلحة (جديد)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = { 
                currentGame = GameType.TICTACTOE
                sendGameEvent("START_GAME:TICTACTOE")
            }, modifier = Modifier.fillMaxWidth(0.85f)) {
                Text("Tic Tac Toe (X O)")
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = { 
                currentGame = GameType.CONNECT4
                sendGameEvent("START_GAME:CONNECT4")
            }, modifier = Modifier.fillMaxWidth(0.85f)) {
                Text("Connect 4 (أربعة على التوالي)")
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = { 
                currentGame = GameType.OTHELLO
                sendGameEvent("START_GAME:OTHELLO")
            }, modifier = Modifier.fillMaxWidth(0.85f)) {
                Text("Othello (ريفرسي)")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(onClick = { 
                currentGame = GameType.NONE
                sendGameEvent("EXIT_GAME")
            }, modifier = Modifier.padding(12.dp)) {
                Text("⬅️ الخروج من اللعبة")
            }
            Box(modifier = Modifier.weight(1f)) {
                when (currentGame) {
                    GameType.CANNON_WAR -> CannonWarGame(activity.gameEventFlow, sendGameEvent)
                    GameType.TICTACTOE -> TicTacToeGame(activity.gameEventFlow, sendGameEvent)
                    GameType.CONNECT4 -> Connect4Game(activity.gameEventFlow, sendGameEvent)
                    GameType.OTHELLO -> OthelloGame(activity.gameEventFlow, sendGameEvent)
                    else -> {}
                }
            }
        }
    }
}
