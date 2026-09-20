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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
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

    var previewView by remember {
        mutableStateOf<PreviewView?>(null)
    }

    var camera by remember {
        mutableStateOf<Camera?>(null)
    }

    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }

    var isFrontCamera by remember {
        mutableStateOf(false)
    }

    var flashEnabled by remember {
        mutableStateOf(false)
    }

    var zoom by remember {
        mutableFloatStateOf(1f)
    }

    var hardwareMaxZoom by remember {
        mutableFloatStateOf(1f)
    }

    /*
     * This is the important part.
     *
     * Camera hardware handles zoom up to its real maximum.
     * Anything above that is done LIVE on the PreviewView.
     *
     * Example:
     *
     * Phone hardware max = 5x
     * User selects 20x
     *
     * Camera = 5x
     * Preview digital scale = 20 / 5 = 4x
     *
     * Total visible zoom = 20x
     */
    fun updateLiveZoom(newZoom: Float) {

        val safeZoom = newZoom.coerceIn(1f, 30f)

        zoom = safeZoom

        val hardwareZoom =
            min(safeZoom, hardwareMaxZoom)

        camera?.cameraControl?.setZoomRatio(hardwareZoom)

        val digitalZoom =
            if (hardwareMaxZoom > 0f) {
                safeZoom / hardwareMaxZoom
            } else {
                1f
            }

        previewView?.scaleX = digitalZoom
        previewView?.scaleY = digitalZoom
    }

    fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val selector =
                if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

            val preview = Preview.Builder()
                .build()

            val capture = ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                )
                .build()

            val view = previewView ?: return@addListener

            preview.setSurfaceProvider(view.surfaceProvider)

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
                    newCamera.cameraInfo.zoomState.value?.maxZoomRatio
                        ?: 1f

                updateLiveZoom(zoom)

                newCamera.cameraControl.enableTorch(false)

            } catch (e: Exception) {

                Toast.makeText(
                    context,
                    "Unable to start camera",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun saveBitmapToGallery(bitmap: Bitmap) {

        val filename =
            "SpaceX_Zoom_${System.currentTimeMillis()}.jpg"

        val resolver = context.contentResolver

        val values = ContentValues().apply {

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

        val uri: Uri? =
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

        if (uri == null) {

            Toast.makeText(
                context,
                "Could not save photo",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            resolver.openOutputStream(uri)?.use { output ->

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

            Toast.makeText(
                context,
                "Photo saved to Gallery",
                Toast.LENGTH_SHORT
            ).show()

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
        }
    }

    fun capturePhoto() {

        val capture = imageCapture ?: return

        val tempFile =
            File.createTempFile(
                "spacex_zoom_",
                ".jpg",
                context.cacheDir
            )

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(
                tempFile
            ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
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

                        /*
                         * CameraX has already applied the real
                         * hardware zoom.
                         *
                         * If the selected zoom is greater than
                         * the hardware maximum, crop the captured
                         * image by the same digital factor that
                         * was shown live on screen.
                         */
                        val digitalFactor =
                            if (hardwareMaxZoom > 0f) {
                                max(
                                    1f,
                                    zoom / hardwareMaxZoom
                                )
                            } else {
                                1f
                            }

                        val cropBitmap: Bitmap

                        if (digitalFactor <= 1.01f) {

                            cropBitmap = original

                        } else {

                            val cropWidth =
                                (original.width / digitalFactor)
                                    .toInt()
                                    .coerceAtLeast(1)

                            val cropHeight =
                                (original.height / digitalFactor)
                                    .toInt()
                                    .coerceAtLeast(1)

                            val left =
                                (original.width - cropWidth) / 2

                            val top =
                                (original.height - cropHeight) / 2

                            cropBitmap =
                                Bitmap.createBitmap(
                                    original,
                                    left,
                                    top,
                                    cropWidth,
                                    cropHeight
                                )
                        }

                        saveBitmapToGallery(cropBitmap)

                        if (cropBitmap !== original) {
                            cropBitmap.recycle()
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
                    clip = true
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 30.dp,
                    bottom = 24.dp
                ),
            verticalArrangement =
                Arrangement.SpaceBetween
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
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

                IconButton(
                    onClick = {

                        flashEnabled =
                            !flashEnabled

                        imageCapture?.flashMode =
                            if (flashEnabled) {
                                ImageCapture.FLASH_MODE_ON
                            } else {
                                ImageCapture.FLASH_MODE_OFF
                            }
                    }
                ) {

                    Icon(
                        imageVector =
                            if (flashEnabled) {
                                Icons.Default.FlashOn
                            } else {
                                Icons.Default.FlashOff
                            },
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.Center
                ) {

                    IconButton(
                        onClick = {
                            isFrontCamera =
                                !isFrontCamera
                        }
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.FlipCameraAndroid,
                            contentDescription =
                                "Switch camera",
                            tint = Color.White
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(30.dp)
                    )

                    IconButton(
                        onClick = {
                            capturePhoto()
                        },
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.CameraAlt,
                            contentDescription =
                                "Take photo",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(38.dp)
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(30.dp)
                    )

                    Text(
                        text = "1x–30x",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
