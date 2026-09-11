package com.tans.tfiletransporter.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow

// P=Pawn, R=Rook, N=Knight, B=Bishop, Q=Queen, K=King
// W=White, B=Black (e.g. WP, BK)

val initialBoard = listOf(
    listOf("BR", "BN", "BB", "BQ", "BK", "BB", "BN", "BR"),
    listOf("BP", "BP", "BP", "BP", "BP", "BP", "BP", "BP"),
    listOf("", "", "", "", "", "", "", ""),
    listOf("", "", "", "", "", "", "", ""),
    listOf("", "", "", "", "", "", "", ""),
    listOf("", "", "", "", "", "", "", ""),
    listOf("WP", "WP", "WP", "WP", "WP", "WP", "WP", "WP"),
    listOf("WR", "WN", "WB", "WQ", "WK", "WB", "WN", "WR")
)

@Composable
fun ChessGame(eventFlow: Flow<String>, sendEvent: (String) -> Unit) {
    var board by remember { mutableStateOf(initialBoard) }
    var myColor by remember { mutableStateOf("") } // "W" or "B"
    var isMyTurn by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf("") }
    
    var selectedPiece by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            if (event.startsWith("CHESS:")) {
                val action = event.removePrefix("CHESS:")
                if (action.startsWith("MOVE:")) {
                    val parts = action.removePrefix("MOVE:").split(",")
                    val sr = parts[0].toInt(); val sc = parts[1].toInt()
                    val er = parts[2].toInt(); val ec = parts[3].toInt()
                    
                    if (myColor.isEmpty()) myColor = "B"
                    
                    val newBoard = board.map { it.toMutableList() }.toMutableList()
                    val captured = newBoard[er][ec]
                    newBoard[er][ec] = newBoard[sr][sc]
                    newBoard[sr][sc] = ""
                    board = newBoard
                    
                    if (captured.endsWith("K")) {
                        winner = if (captured.startsWith("W")) "B" else "W"
                    } else {
                        isMyTurn = true
                    }
                } else if (action == "RESET") {
                    board = initialBoard
                    myColor = ""
                    isMyTurn = false
                    winner = ""
                    selectedPiece = null
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Chess (Simplified)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        val statusText = when {
            winner.isNotEmpty() -> if (winner == myColor) "You Won!" else "You Lost!"
            myColor.isEmpty() -> "Make a move to start as White!"
            isMyTurn -> "Your Turn"
            else -> "Opponent's Turn"
        }
        Text(statusText, style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Chess board
        Box(modifier = Modifier.background(Color.Black).padding(2.dp)) {
            Column {
                for (r in 0 until 8) {
                    Row {
                        for (c in 0 until 8) {
                            val isLight = (r + c) % 2 == 0
                            val bgColor = if (selectedPiece == Pair(r, c)) Color.Yellow 
                                          else if (isLight) Color(0xFFF0D9B5) else Color(0xFFB58863)
                            val piece = board[r][c]
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(bgColor)
                                    .clickable {
                                        if (winner.isNotEmpty() || (!isMyTurn && myColor.isNotEmpty())) return@clickable
                                        
                                        if (selectedPiece == null) {
                                            if (piece.isNotEmpty()) {
                                                if (myColor.isEmpty() && piece.startsWith("W")) {
                                                    myColor = "W"
                                                    selectedPiece = Pair(r, c)
                                                } else if (piece.startsWith(myColor)) {
                                                    selectedPiece = Pair(r, c)
                                                }
                                            }
                                        } else {
                                            val (sr, sc) = selectedPiece!!
                                            if (r == sr && c == sc) {
                                                selectedPiece = null // deselect
                                            } else if (piece.startsWith(myColor)) {
                                                selectedPiece = Pair(r, c) // change selection
                                            } else {
                                                // move (no strict validation here for simplicity, just a relaxed engine)
                                                val newBoard = board.map { it.toMutableList() }.toMutableList()
                                                val captured = newBoard[r][c]
                                                newBoard[r][c] = newBoard[sr][sc]
                                                newBoard[sr][sc] = ""
                                                board = newBoard
                                                
                                                selectedPiece = null
                                                isMyTurn = false
                                                
                                                if (captured.endsWith("K")) {
                                                    winner = myColor
                                                }
                                                sendEvent("CHESS:MOVE:$sr,$sc,$r,$c")
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pieceToUnicode(piece),
                                    fontSize = 28.sp,
                                    color = if (piece.startsWith("W")) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            board = initialBoard
            myColor = ""
            isMyTurn = false
            winner = ""
            selectedPiece = null
            sendEvent("CHESS:RESET")
        }) {
            Text("Restart Game")
        }
    }
}

fun pieceToUnicode(p: String): String {
    return when(p) {
        "WK" -> "♔"; "WQ" -> "♕"; "WR" -> "♖"; "WB" -> "♗"; "WN" -> "♘"; "WP" -> "♙"
        "BK" -> "♚"; "BQ" -> "♛"; "BR" -> "♜"; "BB" -> "♝"; "BN" -> "♞"; "BP" -> "♟"
        else -> ""
    }
}
