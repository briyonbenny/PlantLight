package ie.griffith.plantlight.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import ie.griffith.plantlight.R

/**
 * Shared top bar for the Compose screens.
 *
 * The lectures wrap a screen in Surface + Column, not Scaffold.
 * We still use Scaffold + TopAppBar here so the toolbar sits below
 * the status bar (the Milestone 1 lecturer feedback). The Surface
 * fills the background the same way as Tutorial Two.
 *
 * Colours come from PlantLightTheme so light and dark both work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantLightScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back),
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.primary,
                        titleContentColor = colors.onPrimary,
                        navigationIconContentColor = colors.onPrimary
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            content = content
        )
    }
}
