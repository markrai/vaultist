package com.vaultview.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import coil.compose.SubcomposeAsyncImage
import com.vaultview.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ImageViewerViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: VaultRepository) : ViewModel() {
    val assetId: String = requireNotNull(savedStateHandle["id"])
    val url: String? = repository.assetUrl(assetId)
}

@Composable
fun ImageViewerScreen(onBack: () -> Unit, viewModel: ImageViewerViewModel = hiltViewModel()) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        SubcomposeAsyncImage(
            model = viewModel.url,
            contentDescription = viewModel.assetId,
            contentScale = ContentScale.Fit,
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material.Text("Image could not be loaded") } },
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY).transformable(transformState),
        )
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.Default.ArrowBack, "Back") }
    }
}
