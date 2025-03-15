package ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import rars.Globals
import ui.editors.CodeEditor
import ui.topbar.TopAppBarTool

@Composable
@Preview
fun Home(
    files  : List<String>
) {

    MaterialTheme {

        Scaffold(
            modifier = Modifier.fillMaxSize(),

            topBar = {
                TopAppBarTool()
            }


        ) { innerPadding ->

            CodeEditor(
                innerPadding = innerPadding
            )

        }

    }
}