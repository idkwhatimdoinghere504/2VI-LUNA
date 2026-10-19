package com.twovi.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.twovi.assistant.ui.StudyAssistantApp
import com.twovi.assistant.ui.StudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        setContent {
            StudyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StudyAssistantApp(initialQuestion = sharedText)
                }
            }
        }
    }
}
