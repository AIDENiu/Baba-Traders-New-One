package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import com.example.util.PdfPreviewRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppPdfViewerDialog(
    request: PdfPreviewRequest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

    var renderedPages by remember(request.file.absolutePath, request.file.lastModified()) {
        mutableStateOf<List<Bitmap>>(emptyList())
    }
    var isLoading by remember { mutableStateOf(true) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(request.file.absolutePath, request.file.lastModified()) {
        isLoading = true
        renderError = null
        try {
            val bitmaps = withContext(Dispatchers.IO) {
                val list = mutableListOf<Bitmap>()
                val pfd = ParcelFileDescriptor.open(request.file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                try {
                    val pageCount = renderer.pageCount
                    for (i in 0 until pageCount) {
                        val page = renderer.openPage(i)
                        // Render 5.5" x 8.5" (396 x 612 pt) at 3.0x density for crisp text and lines
                        val width = (page.width * 3.0f).toInt().coerceAtLeast(1)
                        val height = (page.height * 3.0f).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        list.add(bmp)
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                }
                list
            }
            renderedPages = bitmaps
            isLoading = false
        } catch (e: Exception) {
            renderError = e.message ?: "Failed to render PDF document"
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = request.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = request.subtitle,
                                fontSize = 11.sp,
                                color = BabaYellow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_pdf_viewer_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close PDF Viewer",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Zoom Out
                        IconButton(
                            onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f) },
                            enabled = zoomScale > 1f
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Zoom Out",
                                tint = if (zoomScale > 1f) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaYellow
                        )
                        // Zoom In
                        IconButton(
                            onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(2.5f) },
                            enabled = zoomScale < 2.5f
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = if (zoomScale < 2.5f) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        // Print 5.5" x 8.5" Bill
                        IconButton(
                            onClick = {
                                PdfGenerator.printPdfFile(context, request.file)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print 5.5x8.5 Bill",
                                tint = Color.White
                            )
                        }
                        // Open in External PDF App
                        IconButton(
                            onClick = {
                                PdfGenerator.openPdfExternally(
                                    context = context,
                                    file = request.file,
                                    subject = request.shareSubject,
                                    text = request.shareText
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External PDF App",
                                tint = BabaYellow
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BabaRed)
                )
            },
            bottomBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                PdfGenerator.savePdfToDownloads(context, request.file)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BabaDark),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("pdf_viewer_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save PDF",
                                tint = BabaDark,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Save PDF",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaDark
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                PdfGenerator.printPdfFile(context, request.file)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BabaRed),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("pdf_viewer_print_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print 5.5x8.5",
                                tint = BabaRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Print 5.5×8.5",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaRed
                            )
                        }

                        Button(
                            onClick = {
                                PdfGenerator.sharePdfFile(
                                    context = context,
                                    file = request.file,
                                    subject = request.shareSubject,
                                    text = request.shareText
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(44.dp)
                                .testTag("pdf_viewer_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share PDF",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Send / Share",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFF323639) // Classic dark PDF reader backdrop
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = BabaYellow)
                            Text(
                                text = "Rendering PDF Document...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    renderError != null -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = BabaRed,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Unable to preview PDF",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = BabaDark
                                )
                                Text(
                                    text = renderError ?: "",
                                    fontSize = 12.sp,
                                    color = BabaGray
                                )
                                Button(
                                    onClick = {
                                        PdfGenerator.sharePdfFile(
                                            context = context,
                                            file = request.file,
                                            subject = request.shareSubject,
                                            text = request.shareText
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BabaRed)
                                ) {
                                    Text("Share PDF File", color = Color.White)
                                }
                            }
                        }
                    }

                    else -> {
                        val verticalScroll = rememberScrollState()
                        val horizontalScroll = rememberScrollState()
                        val pageDisplayWidth = (screenWidthDp - 24.dp) * zoomScale

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            renderedPages.forEachIndexed { index, bitmap ->
                                val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .width(pageDisplayWidth)
                                        .aspectRatio(aspectRatio)
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "PDF Page ${index + 1}",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "Page ${index + 1} of ${renderedPages.size} • ${request.file.name}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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
