package com.example.easy_video_editor.utils

import kotlin.test.Test
import kotlin.test.assertEquals

// The maths behind rotateVideo and cropVideo on Android: a 90° turn went
// counter-clockwise, and a 16:9 crop of a rotated 160x120 clip came out
// 120x120.
internal class VideoEditMathTest {
  @Test
  fun `a clockwise turn becomes the counter-clockwise degrees Media3 takes`() {
    assertEquals(270f, media3RotationDegrees(90f))
    assertEquals(180f, media3RotationDegrees(180f))
    assertEquals(90f, media3RotationDegrees(270f))
    assertEquals(0f, media3RotationDegrees(0f))
    assertEquals(0f, media3RotationDegrees(360f))
  }

  @Test
  fun `16x9 of a quarter-turned 160x120 clip is 16x9, not square`() {
    // Shown as 120x160 once turned.
    val (width, height) = centredCropFractions(VideoDimensions(120, 160), 16f / 9f)
    assertEquals(120f, 120 * width, 0.001f)
    assertEquals(67.5f, 160 * height, 0.001f)
  }

  @Test
  fun `9x16 of the turned frame keeps its height and cuts the sides`() {
    val (width, height) = centredCropFractions(VideoDimensions(120, 160), 9f / 16f)
    assertEquals(90f, 120 * width, 0.001f)
    assertEquals(160f, 160 * height, 0.001f)
  }

  @Test
  fun `4x3 of the turned frame and 16x9 of an upright one keep the width`() {
    val (turnedWidth, turnedHeight) =
      centredCropFractions(VideoDimensions(120, 160), 4f / 3f)
    assertEquals(120f, 120 * turnedWidth, 0.001f)
    assertEquals(90f, 160 * turnedHeight, 0.001f)

    val (width, height) = centredCropFractions(VideoDimensions(160, 120), 16f / 9f)
    assertEquals(160f, 160 * width, 0.001f)
    assertEquals(90f, 120 * height, 0.001f)
  }

  @Test
  fun `a crop to the frame's own shape keeps it whole`() {
    assertEquals(1f to 1f, centredCropFractions(VideoDimensions(160, 120), 4f / 3f))
  }
}
