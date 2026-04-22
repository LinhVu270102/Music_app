package com.example.music_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.music_app.service.theme.Music_appTheme
import com.google.firebase.auth.FirebaseAuth


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Music_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OrangeMusicTitle(modifier = Modifier.padding(innerPadding))
                }
            }
        }


    }
}
@Preview(showBackground = true)
@Composable
fun OrangeMusicTitlePreview() {
    OrangeMusicTitle()
}
@Composable
fun OrangeMusicTitle(modifier: Modifier = Modifier) {
    Text(
        text = "Orange Music",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 32.dp),
        style = TextStyle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF5500),
                    Color.White,
                    Color.Black
                )
            )
        )
    )
}


