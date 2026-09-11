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
import kotlinx.coroutines.flow.Flow

@Composable
fun TicTacToeGame(eventFlow: Flow<String>, sendEvent: (String) -> Unit) {
    var board by remember { mutableStateOf(List(9) { "" }) }
    var mySymbol by remember { mutableStateOf("") }
    var isMyTurn by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf<String?>(null) }
    
    // When game starts, someone needs to be X and someone O.
    // Let's make whoever clicks first X.
    
    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            if (event.startsWith("TTT:")) {
                val action = event.removePrefix("TTT:")
                if (action.startsWith("MOVE:")) {
                    val index = action.removePrefix("MOVE:").toInt()
                    val newBoard = board.toMutableList()
                    val remoteSymbol = if (mySymbol == "X") "O" else "X"
                    if (mySymbol.isEmpty()) {
                        // They moved first, they are X, I am O
                        mySymbol = "O"
                        newBoard[index] = "X"
                    } else {
                        newBoard[index] = remoteSymbol
                    }
                    board = newBoard
                    isMyTurn = true
                    winner = checkWinner(board)
                } else if (action == "RESET") {
                    board = List(9) { "" }
                    mySymbol = ""
                    isMyTurn = false
                    winner = null
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tic Tac Toe", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        val statusText = when {
            winner != null -> if (winner == "Draw") "It's a Draw!" else if (winner == mySymbol) "You Won!" else "You Lost!"
            mySymbol.isEmpty() -> "Make a move to start as X, or wait!"
            isMyTurn -> "Your Turn ($mySymbol)"
            else -> "Opponent's Turn"
        }
        Text(statusText, style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Column {
            for (i in 0..2) {
                Row {
                    for (j in 0..2) {
                        val index = i * 3 + j
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .padding(4.dp)
                                .background(Color.LightGray)
                                .clickable {
                                    if (winner == null && board[index].isEmpty() && (isMyTurn || mySymbol.isEmpty())) {
                                        if (mySymbol.isEmpty()) mySymbol = "X"
                                        val newBoard = board.toMutableList()
                                        newBoard[index] = mySymbol
                                        board = newBoard
                                        isMyTurn = false
                                        winner = checkWinner(board)
                                        sendEvent("TTT:MOVE:$index")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = board[index],
                                style = MaterialTheme.typography.displayMedium,
                                color = if (board[index] == "X") Color.Blue else Color.Red
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            board = List(9) { "" }
            mySymbol = ""
            isMyTurn = false
            winner = null
            sendEvent("TTT:RESET")
        }) {
            Text("Restart Game")
        }
    }
}

fun checkWinner(b: List<String>): String? {
    val lines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )
    for (line in lines) {
        if (b[line[0]].isNotEmpty() && b[line[0]] == b[line[1]] && b[line[1]] == b[line[2]]) {
            return b[line[0]]
        }
    }
    if (b.none { it.isEmpty() }) return "Draw"
    return null
}
