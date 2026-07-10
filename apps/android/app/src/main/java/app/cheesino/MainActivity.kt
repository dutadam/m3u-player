package app.cheesino

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.cheesino.data.LibraryViewModel
import app.cheesino.ui.RootScreen
import app.cheesino.ui.theme.CheesinoTheme

class MainActivity : ComponentActivity() {
    private val vm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.restore()
        setContent {
            CheesinoTheme {
                RootScreen(vm)
            }
        }
    }
}
