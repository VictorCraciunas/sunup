package dev.victorc.sunup.core.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun reduceMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
