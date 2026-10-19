package com.blackrain02.spamguard.ui.util

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * افکت شیمر بومی برای انیمیشن بارگذاری اسکلتون (Skeleton Loading Shimmer)
 */
@Composable
fun rememberShimmerBrush(targetValue: Float = 1000f): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

/**
 * کامپوننت اسکلتون برای آیتم‌های لیست گفتگو
 */
@Composable
fun ConversationSkeletonItem(brush: Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // آواتار دایره‌ای شیمر
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(brush, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // نام مخاطب
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.45f)
                        .background(brush, RoundedCornerShape(4.dp))
                )
                // زمان پیام
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .width(42.dp)
                        .background(brush, RoundedCornerShape(4.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // خلاصه متن پیام
            Box(
                modifier = Modifier
                    .height(13.dp)
                    .fillMaxWidth(0.75f)
                    .background(brush, RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * لیست اسکلتون گفتگوها هنگام بارگذاری اولیه
 */
@Composable
fun ConversationSkeletonList(count: Int = 8) {
    val brush = rememberShimmerBrush()
    Column(modifier = Modifier.fillMaxSize()) {
        for (i in 0 until count) {
            ConversationSkeletonItem(brush = brush)
        }
    }
}

/**
 * لیست اسکلتون پیام‌ها در صفحه چت (حباب‌های متناوب چپ و راست)
 */
@Composable
fun ChatSkeletonList() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // حباب ورودی چپ
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(48.dp)
                .background(brush, RoundedCornerShape(16.dp))
        )
        // حباب ورودی چپ بزرگتر
        Box(
            modifier = Modifier
                .width(260.dp)
                .height(64.dp)
                .background(brush, RoundedCornerShape(16.dp))
        )
        // حباب ارسالی راست
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .height(52.dp)
                    .background(brush, RoundedCornerShape(16.dp))
            )
        }
        // حباب ورودی چپ
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(40.dp)
                .background(brush, RoundedCornerShape(16.dp))
        )
        // حباب ارسالی راست
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(58.dp)
                    .background(brush, RoundedCornerShape(16.dp))
            )
        }
    }
}

/**
 * آواتار مخاطب با خواندن تصویر واقعی از مخاطبین گوشی و فال‌بک به حروف اول
 */
@Composable
fun ContactAvatar(
    photoUri: String?,
    nameOrAddress: String,
    isUnread: Boolean = false,
    size: Dp = 46.dp,
    isMerged: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        if (!photoUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                        val bm = BitmapFactory.decodeStream(stream)
                        bitmap = bm?.asImageBitmap()
                    }
                } catch (_: Throwable) {
                    bitmap = null
                }
            }
        } else {
            bitmap = null
        }
    }

    val initials = remember(nameOrAddress) {
        nameOrAddress.trim().take(1).uppercase()
    }
    val avatarBg = if (isUnread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val avatarTextColor = if (isUnread) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(avatarBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp,
                    color = avatarTextColor
                )
            }
        }

        // نشان ادغام چند شماره
        if (isMerged) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .size((size.value * 0.42f).dp)
                    .align(Alignment.BottomEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CallMerge,
                        contentDescription = "Merged",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size((size.value * 0.28f).dp)
                    )
                }
            }
        }
    }
}
