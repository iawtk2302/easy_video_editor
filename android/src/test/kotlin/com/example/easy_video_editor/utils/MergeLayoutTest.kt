package com.example.easy_video_editor.utils

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class MergeLayoutTest {
  @Test
  fun `mixed aspect ratio videos fit shared canvas without distortion`() {
    val layout = MergeLayout.from(
      listOf(
        VideoDimensions(width = 1920, height = 1080),
        VideoDimensions(width = 1080, height = 1920),
      )
    )

    assertEquals(VideoDimensions(width = 1920, height = 1920), layout.canvas)

    val landscape = layout.fit(VideoDimensions(width = 1920, height = 1080))
    assertEquals(1920, landscape.width)
    assertEquals(1080, landscape.height)
    assertEquals(0, landscape.offsetX)
    assertEquals(420, landscape.offsetY)
    assertTrue(abs(landscape.aspectRatio - (16.0 / 9.0)) < 0.001)

    val portrait = layout.fit(VideoDimensions(width = 1080, height = 1920))
    assertEquals(1080, portrait.width)
    assertEquals(1920, portrait.height)
    assertEquals(420, portrait.offsetX)
    assertEquals(0, portrait.offsetY)
    assertTrue(abs(portrait.aspectRatio - (9.0 / 16.0)) < 0.001)
  }
}
