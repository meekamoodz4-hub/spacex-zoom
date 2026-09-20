package com.meekamoodz.spacexzoom

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView

    private var imageCapture: ImageCapture? = null

    private var currentCameraSelector =
        CameraSelector.DEFAULT_BACK_CAMERA

    private var cameraProvider:
        ProcessCameraProvider? = null

    private var camera:
        androidx.camera.core.Camera? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            SpaceXZoomApp(
                onPreviewReady = {
                    previewView = it
                    checkCameraPermission()
                },

                onCapture = {
                    takePhoto()
                },

                onSwitchCamera = {
                    switchCamera()
                },

                onToggleTorch = {
                    toggleTorch()
                },

                onZoomChanged = { zoom ->
                    camera?.cameraControl?.setZoomRatio(zoom)
                }
            )
        }
    }

    private fun checkCameraPermission() {

        if (
            androidx.core.content.ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            cameraProvider =
                cameraProviderFuture.get()

            try {

                cameraProvider?.unbindAll()

                val preview =
                    Preview.Builder().build()

                preview.setSurfaceProvider(
                    previewView.surfaceProvider
                )

                imageCapture =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture
                                .CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .build()

                camera =
                    cameraProvider?.bindToLifecycle(
                        this,
                        currentCameraSelector,
                        preview,
                        imageCapture
                    )

            } catch (_: Exception) {
            }

        }, androidx.core.content.ContextCompat
            .getMainExecutor(this))
    }

    private fun switchCamera() {

        currentCameraSelector =
            if (
                currentCameraSelector ==
                CameraSelector.DEFAULT_BACK_CAMERA
            ) {

                CameraSelector.DEFAULT_FRONT_CAMERA

            } else {

                CameraSelector.DEFAULT_BACK_CAMERA
            }

        startCamera()
    }

    private fun toggleTorch() {

        val currentCamera =
            camera ?: return

        if (
            !currentCamera.cameraInfo.hasFlashUnit()
        ) {
            return
        }

        val enabled =
            currentCamera.cameraInfo
                .torchState.value != 1

        currentCamera.cameraControl
            .enableTorch(enabled)
    }

    private fun takePhoto() {

        val capture =
            imageCapture ?: return

        val fileName =
            "SpaceXZoom_" +
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date()) +
                ".jpg"

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            val values =
                ContentValues().apply {

                    put(
                        MediaStore.Images.Media
                            .DISPLAY_NAME,
                        fileName
                    )

                    put(
                        MediaStore.Images.Media
                            .MIME_TYPE,
                        "image/jpeg"
                    )

                    put(
                        MediaStore.Images.Media
                            .RELATIVE_PATH,
                        "Pictures/SpaceX Zoom"
                    )

                    put(
                        MediaStore.Images.Media
                            .IS_PENDING,
                        1
                    )
                }

            val resolver =
                contentResolver

            val uri =
                resolver.insert(
                    MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI,
                    values
                ) ?: return

            val outputOptions =
                ImageCapture
                    .OutputFileOptions
                    .Builder(
                        resolver,
                        uri,
                        ContentValues()
                    )
                    .build()

            capture.takePicture(
                outputOptions,

                androidx.core.content.ContextCompat
                    .getMainExecutor(this),

                object :
                    ImageCapture.OnImageSavedCallback {

                    override fun onError(
                        exception:
                        ImageCaptureException
                    ) {

                        resolver.delete(
                            uri,
                            null,
                            null
                        )
                    }

                    override fun onImageSaved(
                        outputFileResults:
                        ImageCapture.OutputFileResults
                    ) {

                        val completedValues =
                            ContentValues().apply {

                                put(
                                    MediaStore.Images.Media
                                        .IS_PENDING,
                                    0
                                )
                            }

                        resolver.update(
                            uri,
                            completedValues,
                            null,
                            null
                        )
                    }
                }
            )

        } else {

            val directory =
                java.io.File(
                    android.os.Environment
                        .getExternalStoragePublicDirectory(
                            android.os.Environment
                                .DIRECTORY_PICTURES
                        ),
                    "SpaceX Zoom"
                )

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val photoFile =
                java.io.File(
                    directory,
                    fileName
                )

            val outputOptions =
                ImageCapture
                    .OutputFileOptions
                    .Builder(photoFile)
                    .build()

            capture.takePicture(
                outputOptions,

                androidx.core.content.ContextCompat
                    .getMainExecutor(this),

                object :
                    ImageCapture.OnImageSavedCallback {

                    override fun onError(
                        exception:
                        ImageCaptureException
                    ) {
                    }

                    override fun onImageSaved(
                        outputFileResults:
                        ImageCapture.OutputFileResults
                    ) {

                        android.media
                            .MediaScannerConnection
                            .scanFile(
                                this@MainActivity,
                                arrayOf(
                                    photoFile.absolutePath
                                ),
                                arrayOf("image/jpeg"),
                                null
                            )
                    }
                }
            )
        }
    }
}

@Composable
fun SpaceXZoomApp(
    onPreviewReady: (PreviewView) -> Unit,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onZoomChanged: (Float) -> Unit
) {

    var showSplash by remember {
        mutableStateOf(true)
    }

    var zoom by remember {
        mutableFloatStateOf(1f)
    }

    var torchOn by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {

        kotlinx.coroutines.delay(1800)

        showSplash = false
    }

    MaterialTheme {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {

            AndroidView(
                factory = { context ->

                    PreviewView(context).also {

                        it.scaleType =
                            PreviewView.ScaleType.FILL_CENTER

                        onPreviewReady(it)
                    }
                },

                modifier =
                    Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = 42.dp,
                        bottom = 28.dp,
                        start = 18.dp,
                        end = 18.dp
                    ),

                verticalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = "🚀 SpaceX Zoom",
                        color = Color.White,
                        fontSize = 20.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Color.Black.copy(
                                    alpha = 0.55f
                                )
                            )
                            .clickable {

                                torchOn = !torchOn

                                onToggleTorch()
                            },

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Icon(
                            imageVector =
                                if (torchOn)
                                    Icons.Default.FlashOn
                                else
                                    Icons.Default.FlashOff,

                            contentDescription =
                                "Flash",

                            tint = Color.White
                        )
                    }
                }

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "${zoom.toInt()}×",
                        color = Color.White,
                        fontSize = 28.sp
                    )

                    CompositionLocalProvider(
                        LocalLayoutDirection provides
                            LayoutDirection.Ltr
                    ) {

                        Slider(
                            value = zoom,

                            onValueChange = {
                                zoom = it
                                onZoomChanged(it)
                            },

                            valueRange =
                                1f..30f,

                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceEvenly,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Black.copy(
                                        alpha = 0.6f
                                    )
                                )
                                .clickable {
                                    onSwitchCamera()
                                },

                            contentAlignment =
                                Alignment.Center
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default
                                        .FlipCameraAndroid,

                                contentDescription =
                                    "Switch camera",

                                tint = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    onCapture()
                                },

                            contentAlignment =
                                Alignment.Center
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default
                                            .PhotoCamera,

                                    contentDescription =
                                        "Take photo",

                                    tint = Color.White,

                                    modifier =
                                        Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(
                            modifier =
                                Modifier.size(52.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showSplash,

                enter =
                    fadeIn(
                        animationSpec =
                            tween(500)
                    ),

                exit =
                    fadeOut(
                        animationSpec =
                            tween(700)
                    ),

                modifier =
                    Modifier.fillMaxSize()
            ) {

                val transition =
                    rememberInfiniteTransition(
                        label = "logo"
                    )

                val alpha by
                    transition.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,

                        animationSpec =
                            infiniteRepeatable(
                                animation =
                                    tween(
                                        durationMillis =
                                            900,

                                        easing =
                                            LinearEasing
                                    ),

                                repeatMode =
                                    RepeatMode.Reverse
                            ),

                        label = "alpha"
                    )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "🚀",
                            fontSize = 70.sp,
                            modifier =
                                Modifier.alpha(alpha)
                        )

                        Spacer(
                            modifier =
                                Modifier.height(18.dp)
                        )

                        Text(
                            text = "SpaceX Zoom",
                            color = Color.White,
                            fontSize = 30.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Text(
                            text = "See farther.",
                            color = Color.LightGray,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
