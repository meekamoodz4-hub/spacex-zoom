package com.meekamoodz.spacexzoom

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpaceXZoomApp()
        }
    }
}

@Composable
fun SpaceXZoomApp() {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraScreen()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera permission is required",
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    var isFrontCamera by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var hardwareMaxZoom by remember { mutableFloatStateOf(1f) }
    var digitalPreviewZoom by remember { mutableFloatStateOf(1f) }

    // Stores every photo taken during this app session.
    val photos = remember {
        mutableStateListOf<Bitmap>()
    }

    var showPhotoPreview by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }

    fun updateLiveZoom(newZoom: Float) {
        val safeZoom = newZoom.coerceIn(1f, 30f)

        zoom = safeZoom

        val hardwareZoom = min(
            safeZoom,
            hardwareMaxZoom
        )

        camera?.cameraControl?.setZoomRatio(hardwareZoom)

        digitalPreviewZoom =
            if (hardwareMaxZoom > 0f) {
                max(
                    1f,
                    safeZoom / hardwareMaxZoom
                )
            } else {
                1f
            }
    }

    fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                val cameraProvider =
                    cameraProviderFuture.get()

                val selector =
                    if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                val preview =
                    Preview.Builder().build()

                val capture =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .setFlashMode(
                            ImageCapture.FLASH_MODE_OFF
                        )
                        .build()

                val view = previewView
                    ?: return@addListener

                preview.setSurfaceProvider(
                    view.surfaceProvider
                )

                try {
                    cameraProvider.unbindAll()

                    val newCamera =
                        cameraProvider.bindToLifecycle(
                            context as ComponentActivity,
                            selector,
                            preview,
                            capture
                        )

                    camera = newCamera
                    imageCapture = capture

                    hardwareMaxZoom =
                        newCamera.cameraInfo.zoomState.value
                            ?.maxZoomRatio ?: 1f

                    flashEnabled = false

                    updateLiveZoom(zoom)

                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Unable to start camera",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun toggleFlash() {
        val currentCamera = camera
            ?: return

        if (!currentCamera.cameraInfo.hasFlashUnit()) {
            Toast.makeText(
                context,
                "This camera has no flash",
                Toast.LENGTH_SHORT
            ).show()

            flashEnabled = false
            return
        }

        val newState = !flashEnabled

        currentCamera.cameraControl
            .enableTorch(newState)
            .addListener(
                {
                    flashEnabled = newState
                },
                ContextCompat.getMainExecutor(context)
            )
    }

    fun saveBitmapToGallery(
        bitmap: Bitmap
    ): Uri? {

        val filename =
            "SpaceX_Zoom_${System.currentTimeMillis()}.jpg"

        val resolver =
            context.contentResolver

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    filename
                )

                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg"
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES +
                                "/SpaceX Zoom"
                    )

                    put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                    )
                }
            }

        val uri =
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
                ?: run {
                    Toast.makeText(
                        context,
                        "Could not save photo",
                        Toast.LENGTH_SHORT
                    ).show()

                    return null
                }

        return try {

            resolver.openOutputStream(uri)
                ?.use { output ->
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        95,
                        output
                    )
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val finishedValues =
                    ContentValues().apply {
                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            0
                        )
                    }

                resolver.update(
                    uri,
                    finishedValues,
                    null,
                    null
                )
            }

            uri

        } catch (e: Exception) {

            resolver.delete(
                uri,
                null,
                null
            )

            Toast.makeText(
                context,
                "Could not save photo",
                Toast.LENGTH_SHORT
            ).show()

            null
        }
    }

    fun capturePhoto() {

        val capture =
            imageCapture
                ?: return

        val tempFile =
            File.createTempFile(
                "spacex_zoom_",
                ".jpg",
                context.cacheDir
            )

        val outputOptions =
            ImageCapture.OutputFileOptions
                .Builder(tempFile)
                .build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),

            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {

                    try {

                        val original =
                            BitmapFactory.decodeFile(
                                tempFile.absolutePath
                            )

                        if (original == null) {

                            Toast.makeText(
                                context,
                                "Photo capture failed",
                                Toast.LENGTH_SHORT
                            ).show()

                            tempFile.delete()
                            return
                        }

                        val digitalFactor =
                            if (hardwareMaxZoom > 0f) {
                                max(
                                    1f,
                                    zoom / hardwareMaxZoom
                                )
                            } else {
                                1f
                            }

                        val finalBitmap: Bitmap

                        if (digitalFactor <= 1.01f) {

                            finalBitmap = original

                        } else {

                            val cropWidth =
                                (
                                    original.width /
                                            digitalFactor
                                    )
                                    .toInt()
                                    .coerceAtLeast(1)

                            val cropHeight =
                                (
                                    original.height /
                                            digitalFactor
                                    )
                                    .toInt()
                                    .coerceAtLeast(1)

                            val left =
                                (
                                    original.width -
                                            cropWidth
                                    ) / 2

                            val top =
                                (
                                    original.height -
                                            cropHeight
                                    ) / 2

                            finalBitmap =
                                Bitmap.createBitmap(
                                    original,
                                    left,
                                    top,
                                    cropWidth,
                                    cropHeight
                                )
                        }

                        val savedUri =
                            saveBitmapToGallery(
                                finalBitmap
                            )

                        if (savedUri != null) {

                            val viewerBitmap =
                                finalBitmap.copy(
                                    Bitmap.Config.ARGB_8888,
                                    false
                                )

                            // Add the new photo to the history.
                            photos.add(viewerBitmap)

                            // Automatically point viewer at newest photo.
                            selectedPhotoIndex =
                                photos.lastIndex
                        }

                        if (finalBitmap !== original) {
                            finalBitmap.recycle()
                        }

                        original.recycle()
                        tempFile.delete()

                    } catch (e: Exception) {

                        tempFile.delete()

                        Toast.makeText(
                            context,
                            "Could not process photo",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    tempFile.delete()

                    Toast.makeText(
                        context,
                        "Photo capture failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    LaunchedEffect(isFrontCamera) {
        if (previewView != null) {
            startCamera()
        }
    }

    // ---------------- PHOTO VIEWER ----------------

    if (
        showPhotoPreview &&
        photos.isNotEmpty()
    ) {

        val currentPhoto =
            photos[
                selectedPhotoIndex
                    .coerceIn(
                        0,
                        photos.lastIndex
                    )
            ]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {

            Image(
                bitmap = currentPhoto.asImageBitmap(),
                contentDescription = "Photo preview",

                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photos.size) {

                        detectHorizontalDragGestures(

                            onHorizontalDrag = {
                                _, _ ->
                            },

                            onDragEnd = {
                            },

                            onDragCancel = {
                            }
                        )
                    }
                    .pointerInput(photos.size) {

                        var totalDrag = 0f

                        detectHorizontalDragGestures(

                            onHorizontalDrag = {
                                change,
                                dragAmount ->

                                change.consume()

                                totalDrag += dragAmount
                            },

                            onDragEnd = {

                                if (
                                    abs(totalDrag) > 80f
                                ) {

                                    if (
                                        totalDrag < 0f &&
                                        selectedPhotoIndex <
                                        photos.lastIndex
                                    ) {

                                        selectedPhotoIndex++

                                    } else if (
                                        totalDrag > 0f &&
                                        selectedPhotoIndex > 0
                                    ) {

                                        selectedPhotoIndex--
                                    }
                                }

                                totalDrag = 0f
                            },

                            onDragCancel = {
                                totalDrag = 0f
                            }
                        )
                    },

                contentScale = ContentScale.Fit
            )

            // Close button.
            IconButton(
                onClick = {
                    showPhotoPreview = false
                },

                modifier = Modifier
                    .padding(
                        top = 30.dp,
                        start = 12.dp
                    )
                    .size(52.dp)
                    .background(
                        Color.Black.copy(
                            alpha = 0.55f
                        ),
                        CircleShape
                    )
            ) {

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Photo counter.
            Text(
                text =
                    "${selectedPhotoIndex + 1} / ${photos.size}",

                color = Color.White,

                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 45.dp),

                fontSize = 16.sp
            )

            // Swipe instructions.
            if (photos.size > 1) {

                Text(
                    text = "Swipe left or right",

                    color = Color.White.copy(
                        alpha = 0.75f
                    ),

                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 35.dp),

                    fontSize = 14.sp
                )
            }
        }

        return
    }

    // ---------------- CAMERA ----------------

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AndroidView(
            factory = { ctx ->

                PreviewView(ctx).apply {

                    scaleType =
                        PreviewView.ScaleType.FILL_CENTER

                    implementationMode =
                        PreviewView.ImplementationMode.PERFORMANCE

                    previewView = this

                    post {
                        startCamera()
                    }
                }
            },

            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {

                    scaleX =
                        digitalPreviewZoom

                    scaleY =
                        digitalPreviewZoom

                    clip = true
                }
        )

        // TOP BAR

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 30.dp,
                    start = 18.dp,
                    end = 18.dp
                ),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "SpaceX Zoom",
                color = Color.White,
                fontSize = 20.sp
            )

            Row {

                IconButton(
                    onClick = {
                        toggleFlash()
                    }
                ) {

                    Icon(
                        imageVector =
                            if (flashEnabled) {
                                Icons.Default.FlashOn
                            } else {
                                Icons.Default.FlashOff
                            },

                        contentDescription =
                            "Flash",

                        tint = Color.White,

                        modifier =
                            Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = {

                        Toast.makeText(
                            context,
                            "SpaceX Zoom",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Info,

                        contentDescription =
                            "Information",

                        tint = Color.White
                    )
                }
            }
        }

        // ZOOM CONTROL

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = 155.dp,
                    start = 24.dp,
                    end = 24.dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.ZoomIn,

                    contentDescription = null,

                    tint = Color.White
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Text(
                    text =
                        String.format(
                            "%.1fx",
                            zoom
                        ),

                    color = Color.White,

                    fontSize = 18.sp
                )
            }

            Slider(
                value = zoom,

                onValueChange = {
                    updateLiveZoom(it)
                },

                valueRange = 1f..30f,

                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        // BOTTOM CONTROLS

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = 30.dp,
                    start = 28.dp,
                    end = 28.dp
                ),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            // PHOTO THUMBNAIL

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .background(
                        Color.DarkGray
                    )
                    .border(
                        2.dp,
                        Color.White,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        enabled = photos.isNotEmpty()
                    ) {

                        selectedPhotoIndex =
                            photos.lastIndex

                        showPhotoPreview = true
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                if (photos.isNotEmpty()) {

                    Image(
                        bitmap =
                            photos.last()
                                .asImageBitmap(),

                        contentDescription =
                            "Latest photo",

                        modifier =
                            Modifier.fillMaxSize(),

                        contentScale =
                            ContentScale.Crop
                    )

                } else {

                    Icon(
                        imageVector =
                            Icons.Default.CameraAlt,

                        contentDescription =
                            null,

                        tint = Color.White,

                        modifier =
                            Modifier.size(28.dp)
                    )
                }
            }

            // SHUTTER

            IconButton(
                onClick = {
                    capturePhoto()
                },

                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        4.dp,
                        Color.LightGray,
                        CircleShape
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.Default.CameraAlt,

                    contentDescription =
                        "Take photo",

                    tint = Color.Black,

                    modifier =
                        Modifier.size(42.dp)
                )
            }

            // SWITCH CAMERA

            IconButton(
                onClick = {
                    isFrontCamera =
                        !isFrontCamera
                },

                modifier = Modifier
                    .size(58.dp)
                    .background(
                        Color.Black.copy(
                            alpha = 0.45f
                        ),
                        CircleShape
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.Default.FlipCameraAndroid,

                    contentDescription =
                        "Switch camera",

                    tint = Color.White,

                    modifier =
                        Modifier.size(32.dp)
                )
            }
        }
    }
}
