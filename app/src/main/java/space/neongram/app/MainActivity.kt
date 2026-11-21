package space.neongram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import space.neongram.app.core.ui.NeongramTheme
import space.neongram.app.ui.NeongramViewModel
import space.neongram.app.ui.screens.NeongramRootScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeongramTheme {
                val vm: NeongramViewModel = viewModel()
                NeongramRootScreen(viewModel = vm)
            }
        }
    }
}
