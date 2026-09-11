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

@Composable
fun Connect4Game(eventFlow: Flow<String>, sendEvent: (String) -> Unit) {
    val rows = 6
    val cols = 7
    var board by remember { mutableStateOf(List(rows) { MutableList(cols) { 0 } }) }
    var myColor by remember { mutableStateOf(0) } // 1 for Red, 2 for Yellow
    var isMyTurn by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf(0) } // 0 = none, 1 = P1, 2 = P2, 3 = Draw
    
    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            if (event.startsWith("C4:")) {
                val action = event.removePrefix("C4:")
                if (action.startsWith("MOVE:")) {
                    val col = action.removePrefix("MOVE:").toInt()
                    if (myColor == 0) {
                        myColor = 2
                    }
                    val remoteColor = if (myColor == 1) 2 else 1
                    
                    val newBoard = board.map { it.toMutableList() }.toMutableList()
                    for (r in rows - 1 downTo 0) {
                        if (newBoard[r][col] == 0) {
                            newBoard[r][col] = remoteColor
                            break
                        }
                    }
                    board = newBoard
                    isMyTurn = true
                    winner = checkC4Winner(board, rows, cols)
                } else if (action == "RESET") {
                    board = List(rows) { MutableList(cols) { 0 } }
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
        Text("Connect 4", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        val statusText = when {
            winner == 3 -> "It's a Draw!"
            winner != 0 -> if (winner == myColor) "You Won!" else "You Lost!"
            myColor == 0 -> "Make a move to start as Red, or wait!"
            isMyTurn -> "Your Turn"
            else -> "Opponent's Turn"
        }
        Text(statusText, style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.background(Color.Blue).padding(8.dp)) {
            Column {
                for (r in 0 until rows) {
                    Row {
                        for (c in 0 until cols) {
                            val cell = board[r][c]
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(when(cell) {
                                        1 -> Color.Red
                                        2 -> Color.Yellow
                                        else -> Color.White
                                    })
                                    .clickable {
                                        if (winner == 0 && (isMyTurn || myColor == 0) && board[0][c] == 0) {
                                            if (myColor == 0) myColor = 1
                                            val newBoard = board.map { it.toMutableList() }.toMutableList()
                                            for (row in rows - 1 downTo 0) {
                                                if (newBoard[row][c] == 0) {
                                                    newBoard[row][c] = myColor
                                                    break
                                                }
                                            }
                                            board = newBoard
                                            isMyTurn = false
                                            winner = checkC4Winner(board, rows, cols)
                                            sendEvent("C4:MOVE:$c")
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            board = List(rows) { MutableList(cols) { 0 } }
            myColor = 0
            isMyTurn = false
            winner = 0
            sendEvent("C4:RESET")
        }) {
            Text("Restart Game")
        }
    }
}

fun checkC4Winner(b: List<List<Int>>, r: Int, c: Int): Int {
    // Horizontal
    for (row in 0 until r) {
        for (col in 0..c - 4) {
            val p = b[row][col]
            if (p != 0 && p == b[row][col+1] && p == b[row][col+2] && p == b[row][col+3]) return p
        }
    }
    // Vertical
    for (col in 0 until c) {
        for (row in 0..r - 4) {
            val p = b[row][col]
            if (p != 0 && p == b[row+1][col] && p == b[row+2][col] && p == b[row+3][col]) return p
        }
    }
    // Diagonal \
    for (row in 0..r - 4) {
        for (col in 0..c - 4) {
            val p = b[row][col]
            if (p != 0 && p == b[row+1][col+1] && p == b[row+2][col+2] && p == b[row+3][col+3]) return p
        }
    }
    // Diagonal /
    for (row in 3 until r) {
        for (col in 0..c - 4) {
            val p = b[row][col]
            if (p != 0 && p == b[row-1][col+1] && p == b[row-2][col+2] && p == b[row-3][col+3]) return p
        }
    }
    if (b[0].none { it == 0 }) return 3 // Draw
    return 0
}
