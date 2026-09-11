package com.tans.tfiletransporter.ui.games

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.tans.tfiletransporter.ui.filetransport.FileTransportActivity
import com.tans.tfiletransporter.transferproto.fileexplore.requestMsgSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tans.tfiletransporter.logs.AndroidLog

enum class GameType { NONE, TICTACTOE, CONNECT4, OTHELLO }

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
                }
            } else if (jsonStr == "EXIT_GAME") {
                currentGame = GameType.NONE
            }
        }
    }

    if (currentGame == GameType.NONE) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select a Game to Play!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { 
                currentGame = GameType.TICTACTOE
                sendGameEvent("START_GAME:TICTACTOE")
            }, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Tic Tac Toe (X O)")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { 
                currentGame = GameType.CONNECT4
                sendGameEvent("START_GAME:CONNECT4")
            }, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Connect 4")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { 
                currentGame = GameType.OTHELLO
                sendGameEvent("START_GAME:OTHELLO")
            }, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Othello (Reversi)")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(onClick = { 
                currentGame = GameType.NONE
                sendGameEvent("EXIT_GAME")
            }, modifier = Modifier.padding(16.dp)) {
                Text("Exit Game")
            }
            Box(modifier = Modifier.weight(1f)) {
                when (currentGame) {
                    GameType.TICTACTOE -> TicTacToeGame(activity.gameEventFlow, sendGameEvent)
                    GameType.CONNECT4 -> Connect4Game(activity.gameEventFlow, sendGameEvent)
                    GameType.OTHELLO -> OthelloGame(activity.gameEventFlow, sendGameEvent)
                    else -> {}
                }
            }
        }
    }
}
