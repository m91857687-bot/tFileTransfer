package com.tans.tfiletransporter.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

// 0: empty, 1: Black, 2: White
val initialOthelloBoard = List(8) { r ->
    MutableList(8) { c ->
        if (r == 3 && c == 3) 2
        else if (r == 3 && c == 4) 1
        else if (r == 4 && c == 3) 1
        else if (r == 4 && c == 4) 2
        else 0
    }
}

@Composable
fun OthelloGame(eventFlow: Flow<String>, sendEvent: (String) -> Unit) {
    var board by remember { mutableStateOf(initialOthelloBoard.map { it.toMutableList() }) }
    var myColor by remember { mutableStateOf(0) } // 1: Black, 2: White
    var isMyTurn by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf(0) } // 1, 2, 3 (Draw)
    
    // Default starting color is Black (1)
    val currentTurnColor = if (isMyTurn) myColor else if (myColor == 1) 2 else 1
    
    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            if (event.startsWith("OTHELLO:")) {
                val action = event.removePrefix("OTHELLO:")
                if (action.startsWith("MOVE:")) {
                    val parts = action.removePrefix("MOVE:").split(",")
                    val r = parts[0].toInt()
                    val c = parts[1].toInt()
                    
                    if (myColor == 0) myColor = 2 // If opponent moved first, I'm White
                    val opponentColor = if (myColor == 1) 2 else 1
                    
                    val newBoard = board.map { it.toMutableList() }.toMutableList()
                    applyOthelloMove(newBoard, r, c, opponentColor)
                    board = newBoard
                    isMyTurn = true
                    winner = checkOthelloWinner(board)
                } else if (action == "RESET") {
                    board = initialOthelloBoard.map { it.toMutableList() }
                    myColor = 0
                    isMyTurn = false
                    winner = 0
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Othello (Reversi)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        val blackCount = board.flatten().count { it == 1 }
        val whiteCount = board.flatten().count { it == 2 }
        
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Text("Black: $blackCount", style = MaterialTheme.typography.titleMedium, color = Color.Black)
            Text("White: $whiteCount", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val statusText = when {
            winner == 3 -> "It's a Draw!"
            winner != 0 -> if (winner == myColor) "You Won!" else "You Lost!"
            myColor == 0 -> "Make a move to start as Black!"
            isMyTurn -> "Your Turn"
            else -> "Opponent's Turn"
        }
        Text(statusText, style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.background(Color(0xFF2E7D32)).padding(4.dp)) {
            Column {
                for (r in 0 until 8) {
                    Row {
                        for (c in 0 until 8) {
                            val piece = board[r][c]
                            val isValid = isValidOthelloMove(board, r, c, if (myColor == 0) 1 else myColor)
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(2.dp)
                                    .background(Color(0xFF4CAF50))
                                    .clickable {
                                        if (winner == 0 && (isMyTurn || myColor == 0) && isValid) {
                                            if (myColor == 0) myColor = 1 // I play Black
                                            
                                            val newBoard = board.map { it.toMutableList() }.toMutableList()
                                            applyOthelloMove(newBoard, r, c, myColor)
                                            board = newBoard
                                            isMyTurn = false
                                            winner = checkOthelloWinner(board)
                                            sendEvent("OTHELLO:MOVE:$r,$c")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (piece != 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(if (piece == 1) Color.Black else Color.White)
                                    )
                                } else if (isValid && (isMyTurn || myColor == 0)) {
                                     Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x88000000))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            board = initialOthelloBoard.map { it.toMutableList() }
            myColor = 0
            isMyTurn = false
            winner = 0
            sendEvent("OTHELLO:RESET")
        }) {
            Text("Restart Game")
        }
    }
}

fun isValidOthelloMove(board: List<List<Int>>, r: Int, c: Int, color: Int): Boolean {
    if (board[r][c] != 0) return false
    val opponent = if (color == 1) 2 else 1
    val dirs = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
    
    for (d in dirs) {
        var nr = r + d.first
        var nc = c + d.second
        var foundOpponent = false
        while (nr in 0 until 8 && nc in 0 until 8 && board[nr][nc] == opponent) {
            foundOpponent = true
            nr += d.first
            nc += d.second
        }
        if (foundOpponent && nr in 0 until 8 && nc in 0 until 8 && board[nr][nc] == color) {
            return true
        }
    }
    return false
}

fun applyOthelloMove(board: MutableList<MutableList<Int>>, r: Int, c: Int, color: Int) {
    board[r][c] = color
    val opponent = if (color == 1) 2 else 1
    val dirs = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
    
    for (d in dirs) {
        var nr = r + d.first
        var nc = c + d.second
        val flips = mutableListOf<Pair<Int, Int>>()
        
        while (nr in 0 until 8 && nc in 0 until 8 && board[nr][nc] == opponent) {
            flips.add(Pair(nr, nc))
            nr += d.first
            nc += d.second
        }
        if (flips.isNotEmpty() && nr in 0 until 8 && nc in 0 until 8 && board[nr][nc] == color) {
            for (f in flips) {
                board[f.first][f.second] = color
            }
        }
    }
}

fun checkOthelloWinner(board: List<List<Int>>): Int {
    var canBlackMove = false
    var canWhiteMove = false
    for (r in 0 until 8) {
        for (c in 0 until 8) {
            if (isValidOthelloMove(board, r, c, 1)) canBlackMove = true
            if (isValidOthelloMove(board, r, c, 2)) canWhiteMove = true
        }
    }
    if (canBlackMove || canWhiteMove) return 0 // Game still going
    
    val blackCount = board.flatten().count { it == 1 }
    val whiteCount = board.flatten().count { it == 2 }
    return if (blackCount > whiteCount) 1
           else if (whiteCount > blackCount) 2
           else 3 // Draw
}
