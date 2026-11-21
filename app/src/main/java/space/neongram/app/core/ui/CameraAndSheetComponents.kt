package space.neongram.app.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import space.neongram.app.core.model.DevicePhotoItem
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * 1. Real CameraX + ML Kit Barcode / QR Scanner Box with 2026 Liquid Glass Neon Viewfinder Overlay.
 */
@Composable
fun RealQrScannerBox(
    modifier: Modifier = Modifier,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scheme = LocalNeonScheme.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var lastScannedValue by remember { mutableStateOf("") }
    var lastScannedTimestamp by remember { mutableStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "QrLaserScan")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )
    val cornerGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerGlow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF070918),
                        Color(0xFF101532)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.55f),
                        scheme.accent.copy(alpha = 0.45f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
            val barcodeScanner = remember {
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                BarcodeScanning.getClient(options)
            }

            DisposableEffect(Unit) {
                onDispose {
                    try {
                        barcodeScanner.close()
                    } catch (_: Exception) {
                    }
                    try {
                        cameraExecutor.shutdown()
                    } catch (_: Exception) {
                    }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                processQrImageProxy(
                                    barcodeScanner = barcodeScanner,
                                    imageProxy = imageProxy,
                                    onQrDetected = { rawValue ->
                                        val now = System.currentTimeMillis()
                                        if (rawValue.isNotBlank() &&
                                            (rawValue != lastScannedValue || now - lastScannedTimestamp > 3500L)
                                        ) {
                                            lastScannedValue = rawValue
                                            lastScannedTimestamp = now
                                            previewView.post {
                                                onQrCodeScanned(rawValue)
                                            }
                                        }
                                    }
                                )
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Darkened periphery vignette around viewfinder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x88040612)
                            ),
                            radius = 520f
                        )
                    )
            )

            // Liquid Glass Neon Viewfinder Overlay (220.dp x 220.dp rounded-28 frame)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cornerLen = 36.dp.toPx()
                    val strokeW = 3.6.dp.toPx()
                    val cyanGlow = scheme.primary.copy(alpha = cornerGlowAlpha)
                    val violetGlow = scheme.accent.copy(alpha = cornerGlowAlpha)

                    // Top-Left corner
                    drawLine(
                        color = cyanGlow,
                        start = Offset(0f, cornerLen),
                        end = Offset(0f, 12.dp.toPx()),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = cyanGlow,
                        start = Offset(12.dp.toPx(), 0f),
                        end = Offset(cornerLen, 0f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Top-Right corner
                    drawLine(
                        color = violetGlow,
                        start = Offset(w - cornerLen, 0f),
                        end = Offset(w - 12.dp.toPx(), 0f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = violetGlow,
                        start = Offset(w, 12.dp.toPx()),
                        end = Offset(w, cornerLen),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Bottom-Left corner
                    drawLine(
                        color = violetGlow,
                        start = Offset(0f, h - cornerLen),
                        end = Offset(0f, h - 12.dp.toPx()),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = violetGlow,
                        start = Offset(12.dp.toPx(), h),
                        end = Offset(cornerLen, h),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Bottom-Right corner
                    drawLine(
                        color = cyanGlow,
                        start = Offset(w - cornerLen, h),
                        end = Offset(w - 12.dp.toPx(), h),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = cyanGlow,
                        start = Offset(w, h - cornerLen),
                        end = Offset(w, h - 12.dp.toPx()),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Animated Laser Scan Line
                    val laserY = h * laserProgress
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                scheme.primary.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            startY = (laserY - 18.dp.toPx()).coerceAtLeast(0f),
                            endY = (laserY + 18.dp.toPx()).coerceAtMost(h)
                        ),
                        topLeft = Offset(12.dp.toPx(), (laserY - 14.dp.toPx()).coerceAtLeast(0f)),
                        size = Size(w - 24.dp.toPx(), 28.dp.toPx()),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                scheme.primary,
                                Color.White,
                                scheme.accent,
                                Color.Transparent
                            )
                        ),
                        start = Offset(14.dp.toPx(), laserY),
                        end = Offset(w - 14.dp.toPx(), laserY),
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Bottom subtle glass hint pill inside camera box
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xAA080B1E))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (lastScannedValue.isNotBlank()) "QR распознан ✓" else "Наведите камеру на QR-код Neongram",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Frosted glass card requesting Camera permission
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x66161B3A))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    scheme.primary.copy(alpha = 0.25f),
                                    scheme.accent.copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(1.dp, scheme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = "Живой QR-сканер камеры",
                    color = scheme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Для мгновенного входа по QR-коду на ПК или другом телефоне разрешите доступ к камере",
                    color = scheme.muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(scheme.primary, scheme.accent)
                            )
                        )
                        .clickable {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Разрешить доступ к камере",
                        color = Color(0xFF050713),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processQrImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onQrDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val raw = barcode.rawValue
                    if (!raw.isNullOrBlank()) {
                        onQrDetected(raw.trim())
                        break
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

/**
 * 2. Telegram-Style Draggable Liquid Glass Attachment Sheet
 * Supports free continuous anchoring anywhere between 50% and 80% of screen height!
 */
@Composable
fun TelegramDraggableAttachmentSheet(
    visible: Boolean,
    isAiChat: Boolean = false,
    aiModel: String = "fast",
    aiMode: String = "default",
    devicePhotos: List<DevicePhotoItem> = emptyList(),
    onDismiss: () -> Unit,
    onSelectAiModel: (String) -> Unit = {},
    onSelectAiMode: (String) -> Unit = {},
    onSendPhotoUris: (List<Uri>, String) -> Unit,
    onPickSystemGallery: () -> Unit,
    onPickSystemFile: () -> Unit,
    onOpenPermissionReadImages: () -> Unit = {}
) {
    val scheme = LocalNeonScheme.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenHeightDp = configuration.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx().coerceAtLeast(1f) }

    // Continuous sheet height fraction between 0.50f and 0.80f (default 0.58f = 58%)
    var sheetFraction by remember { mutableFloatStateOf(0.58f) }
    var isDraggingHandle by remember { mutableStateOf(false) }

    // Active bottom tab: 0 = Галерея, 1 = Файл, 2 = Альбомы, 3 = ИИ
    var activeTab by remember { mutableIntStateOf(0) }
    val selectedUriStrings = remember { mutableStateListOf<String>() }
    var photoCaption by remember { mutableStateOf("") }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasMediaPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                mediaPermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMediaPermission = granted
        if (granted) {
            onOpenPermissionReadImages()
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            if (sheetFraction < 0.50f || sheetFraction > 0.80f) {
                sheetFraction = 0.58f
            }
            val currentlyGranted = ContextCompat.checkSelfPermission(
                context,
                mediaPermission
            ) == PackageManager.PERMISSION_GRANTED
            hasMediaPermission = currentlyGranted
            if (currentlyGranted) {
                onOpenPermissionReadImages()
            } else {
                mediaPermissionLauncher.launch(mediaPermission)
            }
        } else {
            selectedUriStrings.clear()
            photoCaption = ""
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDismiss()
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            val currentPercent = (sheetFraction * 100f).roundToInt().coerceIn(32, 85)

            // Frosted Liquid Glass Sheet Container anchored at exact continuous `sheetFraction`
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = sheetFraction.coerceIn(0.32f, 0.85f))
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xF210142E),
                                Color(0xFA080A1A)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = if (isDraggingHandle) 0.85f else 0.45f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks inside sheet */ }
                    .navigationBarsPadding()
            ) {
                // Top Draggable Grabber Handle & Header Bar (continuous 50%..80% drag zone)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(screenHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDraggingHandle = true
                                },
                                onDragEnd = {
                                    isDraggingHandle = false
                                    if (sheetFraction < 0.42f) {
                                        sheetFraction = 0.58f
                                        onDismiss()
                                    } else {
                                        // Keep sheetFraction at the EXACT continuous position where user released it inside 50%..80%
                                        sheetFraction = sheetFraction.coerceIn(0.50f, 0.80f)
                                    }
                                },
                                onDragCancel = {
                                    isDraggingHandle = false
                                    sheetFraction = sheetFraction.coerceIn(0.50f, 0.80f)
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    sheetFraction = (sheetFraction - (dragAmount / screenHeightPx))
                                        .coerceIn(0.32f, 0.85f)
                                }
                            )
                        }
                        .padding(top = 10.dp, bottom = 8.dp, start = 18.dp, end = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Glowing cyan/white drag handle pill (44.dp x 5.dp)
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        scheme.primary.copy(alpha = if (isDraggingHandle) 1f else 0.75f),
                                        Color.White.copy(alpha = 0.9f),
                                        scheme.accent.copy(alpha = if (isDraggingHandle) 1f else 0.75f)
                                    )
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    activeTab == 3 || (isAiChat && activeTab == 3) -> "Neongram AI Настройки"
                                    activeTab == 1 -> "Файлы и документы"
                                    activeTab == 2 -> "Альбомы устройства"
                                    else -> "Вложения / Галерея"
                                },
                                color = scheme.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Потяните шторку на любую высоту от 50% до 80%",
                                color = scheme.muted,
                                fontSize = 11.sp
                            )
                        }

                        // Subtle glass badge showing exact current height percentage (50%..80%)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isDraggingHandle) {
                                            scheme.primary.copy(alpha = 0.24f)
                                        } else {
                                            Color.White.copy(alpha = 0.08f)
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isDraggingHandle) scheme.primary else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable {
                                        // Cycle smooth continuous presets or let user drag freely
                                        sheetFraction = when {
                                            sheetFraction < 0.56f -> 0.65f
                                            sheetFraction < 0.74f -> 0.80f
                                            else -> 0.50f
                                        }
                                    }
                                    .padding(horizontal = 11.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${currentPercent}%",
                                    color = if (isDraggingHandle) scheme.primary else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = scheme.text,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Main Sheet Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        0, 2 -> {
                            // Tab 0 & 2: Галерея (Альбомы) — Telegram-style 3-column instant photo grid
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp)
                            ) {
                                // Top Quick Action Row: System Album Picker + Refresh/Permissions
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        scheme.primary.copy(alpha = 0.20f),
                                                        scheme.accent.copy(alpha = 0.20f)
                                                    )
                                                )
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = scheme.primary.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable { onPickSystemGallery() }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoLibrary,
                                                contentDescription = null,
                                                tint = scheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Открыть системный альбом / Фото",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (selectedUriStrings.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                                .clickable { selectedUriStrings.clear() }
                                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "Сброс (${selectedUriStrings.size})",
                                                color = scheme.muted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                if (devicePhotos.isEmpty()) {
                                    // Empty or permission needed state
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(Color.White.copy(alpha = 0.04f))
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = scheme.primary,
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = if (hasMediaPermission) {
                                                "Фотографии в быстрой сетке не найдены"
                                            } else {
                                                "Разрешите доступ к галерее для мгновенной сетки фото как в Telegram"
                                            },
                                            color = scheme.text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (!hasMediaPermission) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(scheme.primary)
                                                        .clickable {
                                                            mediaPermissionLauncher.launch(mediaPermission)
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 9.dp)
                                                ) {
                                                    Text(
                                                        text = "Разрешить доступ к фото",
                                                        color = Color(0xFF050713),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(Color.White.copy(alpha = 0.12f))
                                                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                                                    .clickable { onPickSystemGallery() }
                                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                                            ) {
                                                Text(
                                                    text = "Выбрать из альбома",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // 3-Column Photo Grid (LazyVerticalGrid(GridCells.Fixed(3)))
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentPadding = PaddingValues(bottom = if (selectedUriStrings.isNotEmpty()) 136.dp else 76.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(
                                            items = devicePhotos,
                                            key = { it.uriString }
                                        ) { photoItem ->
                                            val selectionIndex = selectedUriStrings.indexOf(photoItem.uriString)
                                            val isSelected = selectionIndex >= 0

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF141834))
                                                    .border(
                                                        width = if (isSelected) 2.2.dp else 0.8.dp,
                                                        color = if (isSelected) scheme.primary else Color.White.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                    .clickable {
                                                        if (isSelected) {
                                                            selectedUriStrings.remove(photoItem.uriString)
                                                        } else {
                                                            selectedUriStrings.add(photoItem.uriString)
                                                        }
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = Uri.parse(photoItem.uriString),
                                                    contentDescription = photoItem.displayName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(if (isSelected) 3.dp else 0.dp)
                                                        .clip(RoundedCornerShape(if (isSelected) 11.dp else 14.dp))
                                                )

                                                // Subtle dark scrim when selected
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color(0x33040714))
                                                    )
                                                }

                                                // Top-right Telegram-style glowing cyan numbered checkmark badge
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) {
                                                                Brush.linearGradient(
                                                                    colors = listOf(scheme.primary, scheme.accent)
                                                                )
                                                            } else {
                                                                Brush.linearGradient(
                                                                    colors = listOf(
                                                                        Color(0x88060818),
                                                                        Color(0x88060818)
                                                                    )
                                                                )
                                                            }
                                                        )
                                                        .border(
                                                            width = 1.5.dp,
                                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                                            shape = CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Text(
                                                            text = "${selectionIndex + 1}",
                                                            color = Color(0xFF050713),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Tab 1: Файлы
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .padding(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    scheme.primary.copy(alpha = 0.22f),
                                                    scheme.accent.copy(alpha = 0.22f)
                                                )
                                            )
                                        )
                                        .border(1.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                        .clickable { onPickSystemFile() }
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(scheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = scheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Выбрать документ или архив с устройства",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "PDF, ZIP, DOCX, APK, аудио и фото без сжатия",
                                                color = scheme.muted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                        .clickable { onPickSystemGallery() }
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(scheme.accent.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = scheme.accent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Отправить фото или видео из галереи",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Открыть системный выбор медиафайлов",
                                                color = scheme.muted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Tab 2/3: ИИ Режим (Model & Mode selector pills)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .padding(bottom = 84.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Модель Neongram AI",
                                    color = scheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val models = listOf(
                                    Triple("fast", "⚡ Быстрая", "Мгновенные ответы на каждый день"),
                                    Triple("thinking", "🧠 Думающая", "Глубокое рассуждение и логика"),
                                    Triple("best", "💎 Лучшая", "Максимальная точность и креатив")
                                )

                                models.forEach { (id, title, subtitle) ->
                                    val selected = aiModel.equals(id, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                if (selected) scheme.primary.copy(alpha = 0.18f)
                                                else Color.White.copy(alpha = 0.05f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (selected) scheme.primary else Color.White.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .clickable { onSelectAiModel(id) }
                                            .padding(horizontal = 14.dp, vertical = 11.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = title,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = subtitle,
                                                    color = scheme.muted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = scheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Режим ИИ-ассистента",
                                    color = scheme.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val modes = listOf(
                                    "default" to "✨ Обычный",
                                    "study" to "🎓 Учебный",
                                    "psychologist" to "💜 Психолог",
                                    "coding" to "💻 Кодинг"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    modes.take(2).forEach { (id, label) ->
                                        val selected = aiMode.equals(id, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (selected) scheme.accent.copy(alpha = 0.24f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (selected) scheme.accent else Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable { onSelectAiMode(id) }
                                                .padding(vertical = 11.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    modes.drop(2).forEach { (id, label) ->
                                        val selected = aiMode.equals(id, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (selected) scheme.accent.copy(alpha = 0.24f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (selected) scheme.accent else Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable { onSelectAiMode(id) }
                                                .padding(vertical = 11.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Floating Stack: Selected Photos Caption + Send Bar AND Telegram Pill Switcher
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // When 1+ photos are selected, show floating Liquid Glass caption & Send bar
                        AnimatedVisibility(visible = selectedUriStrings.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xEE101532))
                                    .border(
                                        width = 1.dp,
                                        color = scheme.primary.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (photoCaption.isEmpty()) {
                                        Text(
                                            text = "Добавить подпись к фото...",
                                            color = scheme.muted,
                                            fontSize = 13.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = photoCaption,
                                        onValueChange = { photoCaption = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 13.sp
                                        ),
                                        cursorBrush = SolidColor(scheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(scheme.primary, scheme.accent)
                                            )
                                        )
                                        .clickable {
                                            val uris = selectedUriStrings.map { Uri.parse(it) }
                                            val captionToSend = photoCaption.trim()
                                            onSendPhotoUris(uris, captionToSend)
                                            selectedUriStrings.clear()
                                            photoCaption = ""
                                        }
                                        .padding(horizontal = 16.dp, vertical = 9.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            tint = Color(0xFF050713),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Отправить (${selectedUriStrings.size})",
                                            color = Color(0xFF050713),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Telegram Pill Switcher inside the Sheet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xE60B0E24))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = buildList {
                                add(0 to "🖼️ Галерея")
                                add(1 to "📁 Файл")
                                add(2 to "📷 Альбомы")
                                if (isAiChat) {
                                    add(3 to "✨ ИИ")
                                }
                            }

                            tabs.forEach { (tabIndex, label) ->
                                val isSelected = activeTab == tabIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        scheme.primary.copy(alpha = 0.28f),
                                                        scheme.accent.copy(alpha = 0.28f)
                                                    )
                                                )
                                            } else {
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color.Transparent, Color.Transparent)
                                                )
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) scheme.primary.copy(alpha = 0.55f) else Color.Transparent,
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .clickable {
                                            activeTab = tabIndex
                                            if (tabIndex == 2) {
                                                onPickSystemGallery()
                                            }
                                        }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else scheme.muted,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
