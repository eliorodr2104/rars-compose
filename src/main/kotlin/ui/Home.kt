package ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import rars.Globals

@Composable
@Preview
fun Home(
    nameApp: String = "",
    files  : List<String>
) {

    // the "restore" size (window control button that toggles with maximize)
    // I want to keep it large, with enough room for user to get handles
    Globals.initialize()

    var text by remember { mutableStateOf("Hello, World!") }

    MaterialTheme {
        Button(onClick = {
            text = "Hello, Desktop!"
        }) {
            Text(text)
        }
    }
}