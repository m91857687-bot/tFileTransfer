package com.tans.tfiletransporter.ui.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.flow.MutableSharedFlow

class CannonWarActivity : AppCompatActivity() {

    private val localEventFlow = MutableSharedFlow<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CannonWarGame(
                eventFlow = localEventFlow,
                sendEvent = { /* Standalone mode */ }
            )
        }
    }
}
