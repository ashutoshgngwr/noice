package com.github.ashutoshgngwr.noice.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.model.Sound
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class PlaybackControllerTest {

  private lateinit var mockPlayerManager: PlayerManager

  @Before
  fun setup() {
    mockPlayerManager = mockk(relaxed = true)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testBuildResumeActionPendingIntent() {
    val pendingIntent = PlaybackController.buildResumeActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_RESUME_PLAYBACK, intent.action)
  }

  @Test
  fun testBuildPauseActionPendingIntent() {
    val pendingIntent = PlaybackController.buildPauseActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_PAUSE_PLAYBACK, intent.action)
  }

  @Test
  fun testBuildStopActionPendingIntent() {
    val pendingIntent = PlaybackController.buildStopActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_STOP_PLAYBACK, intent.action)
  }

  @Test
  fun testBuildSkipPrevActionPendingIntent() {
    val pendingIntent = PlaybackController.buildSkipPrevActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_SKIP_PRESET, intent.action)
    assertEquals(
      PlayerManager.SKIP_DIRECTION_PREV,
      intent.getIntExtra(PlaybackController.EXTRA_SKIP_DIRECTION, 0)
    )
  }

  @Test
  fun testBuildSkipNextActionPendingIntent() {
    val pendingIntent = PlaybackController.buildSkipNextActionPendingIntent(
      ApplicationProvider.getApplicationContext()
    )

    val intent = shadowOf(pendingIntent).savedIntent
    assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
    assertEquals(PlaybackController.ACTION_SKIP_PRESET, intent.action)
    assertEquals(
      PlayerManager.SKIP_DIRECTION_NEXT,
      intent.getIntExtra(PlaybackController.EXTRA_SKIP_DIRECTION, 0)
    )
  }

  @Test
  fun testBuildAlarmPendingIntent() {
    val inputShouldUpdateMediaVolume = arrayOf(true, false)
    val outputVolumes = arrayOf(10, -1)
    for (i in inputShouldUpdateMediaVolume.indices) {
      val presetID = "test-preset-id"
      val volume = 10
      val pendingIntent = PlaybackController.buildAlarmPendingIntent(
        ApplicationProvider.getApplicationContext(),
        presetID, inputShouldUpdateMediaVolume[i], volume
      )

      val intent = shadowOf(pendingIntent).savedIntent
      assertEquals(MediaPlayerService::class.qualifiedName, intent.component?.className)
      assertEquals(PlaybackController.ACTION_PLAY_PRESET, intent.action)
      assertEquals(presetID, intent.getStringExtra(PlaybackController.EXTRA_PRESET_ID))
      assertEquals(
        outputVolumes[i],
        intent.getIntExtra(PlaybackController.EXTRA_DEVICE_MEDIA_VOLUME, -1)
      )
    }
  }

  @Test
  fun testHandleServiceIntent_withResumePlaybackAction() {
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_RESUME_PLAYBACK),
      mockk()
    )

    verify(exactly = 1) { mockPlayerManager.resume() }
  }

  @Test
  fun testHandleServiceIntent_withPausePlaybackAction() {
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PAUSE_PLAYBACK),
      mockk()
    )

    verify(exactly = 1) { mockPlayerManager.pause() }
  }

  @Test
  fun testHandleServiceIntent_withStopPlaybackAction() {
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_STOP_PLAYBACK),
      mockk()
    )

    verify(exactly = 1) { mockPlayerManager.stop() }
  }

  @Test
  fun testHandleServiceIntent_withPlayPresetAction() {
    val presetID = "test-id"
    val volume = Random.nextInt(10)

    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PLAY_PRESET)
        .putExtra(PlaybackController.EXTRA_PRESET_ID, presetID)
        .putExtra(PlaybackController.EXTRA_DEVICE_MEDIA_VOLUME, volume),
      mockk()
    )

    verifySequence { mockPlayerManager.playPreset(presetID) }

    assertEquals(
      volume,
      ApplicationProvider.getApplicationContext<Context>()
        .getSystemService<AudioManager>()
        ?.getStreamVolume(AudioManager.STREAM_MUSIC)
    )
  }

  @Test
  fun testHandleServiceIntent_withPlayRandomPresetAction() {
    val tag = mockk<Sound.Tag>()
    val minSounds = Random.nextInt()
    val maxSounds = Random.nextInt(minSounds, minSounds + 10)
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PLAY_RANDOM_PRESET)
        .putExtra(PlaybackController.EXTRA_FILTER_SOUNDS_BY_TAG, tag)
        .putExtra(PlaybackController.EXTRA_RANDOM_PRESET_MIN_SOUNDS, minSounds)
        .putExtra(PlaybackController.EXTRA_RANDOM_PRESET_MAX_SOUNDS, maxSounds),
      mockk()
    )

    verifySequence { mockPlayerManager.playRandomPreset(tag, minSounds..maxSounds) }
  }

  @Test
  fun testHandleServiceIntent_withPlaySoundAction() {
    val soundKey = "test-sound-key"
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_PLAY_SOUND)
        .putExtra(PlaybackController.EXTRA_SOUND_KEY, soundKey),
      mockk()
    )

    verify(exactly = 1) { mockPlayerManager.play(soundKey) }
  }

  @Test
  fun testHandleServiceIntent_withStopSoundAction() {
    val soundKey = "test-sound-key"
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_STOP_SOUND)
        .putExtra(PlaybackController.EXTRA_SOUND_KEY, soundKey),
      mockk()
    )

    verify(exactly = 1) { mockPlayerManager.stop(soundKey) }
  }

  @Test
  fun testHandleServiceIntent_withScheduleStopPlaybackAction_onSchedule() {
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() + 100),
      Handler(Looper.getMainLooper())
    )

    verify { mockPlayerManager wasNot called }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify(exactly = 1) { mockPlayerManager.pause() }
  }

  @Test
  fun testHandleServiceIntent_withScheduleStopPlaybackAction_onCancel() {
    val handler = Handler(Looper.getMainLooper())
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() + 1000),
      handler
    )

    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() - 1000),
      handler
    )

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify { mockPlayerManager wasNot called }
  }

  @Test
  fun testHandleServiceIntent_withoutAction() {
    PlaybackController.handleServiceIntent(
      ApplicationProvider.getApplicationContext(),
      mockPlayerManager,
      Intent(),
      mockk()
    )

    verify { mockPlayerManager wasNot called }
  }

  @Test
  fun testPlaySound() {
    val mockContext = mockk<Context>(relaxed = true)
    val soundKey = "test-sound-key"
    PlaybackController.play(mockContext, soundKey)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PLAY_SOUND, it.action)
          assertEquals(soundKey, it.getStringExtra(PlaybackController.EXTRA_SOUND_KEY))
        }
      )
    }
  }

  @Test
  fun testStopSound() {
    val mockContext = mockk<Context>(relaxed = true)
    val soundKey = "test-sound-key"
    PlaybackController.stop(mockContext, soundKey)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_STOP_SOUND, it.action)
          assertEquals(soundKey, it.getStringExtra(PlaybackController.EXTRA_SOUND_KEY))
        }
      )
    }
  }

  @Test
  fun testResumePlayback() {
    val mockContext = mockk<Context>(relaxed = true)
    PlaybackController.resume(mockContext)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_RESUME_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testPausePlayback() {
    val mockContext = mockk<Context>(relaxed = true)
    PlaybackController.pause(mockContext)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PAUSE_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testStopPlayback() {
    val mockContext = mockk<Context>(relaxed = true)
    PlaybackController.stop(mockContext)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_STOP_PLAYBACK, it.action)
        }
      )
    }
  }

  @Test
  fun testPlayPreset() {
    val mockContext = mockk<Context>(relaxed = true)
    PlaybackController.playPreset(mockContext, "test")
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PLAY_PRESET, it.action)
          assertEquals("test", it.getStringExtra(PlaybackController.EXTRA_PRESET_ID))
        }
      )
    }
  }

  @Test
  fun testPlayRandomPreset() {
    val mockContext = mockk<Context>(relaxed = true)
    val tag = mockk<Sound.Tag>(relaxed = true)
    val intensity = 1 until 10

    PlaybackController.playRandomPreset(mockContext, tag, intensity)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_PLAY_RANDOM_PRESET, it.action)
          assertEquals(
            intensity.first,
            it.getIntExtra(PlaybackController.EXTRA_RANDOM_PRESET_MIN_SOUNDS, 0)
          )

          assertEquals(
            intensity.last,
            it.getIntExtra(PlaybackController.EXTRA_RANDOM_PRESET_MAX_SOUNDS, 0)
          )

          assertEquals(
            tag,
            it.getSerializableExtra(PlaybackController.EXTRA_FILTER_SOUNDS_BY_TAG) as Sound.Tag
          )
        }
      )
    }
  }

  @Test
  fun testScheduleAutoStop() {
    mockkStatic(PreferenceManager::class)
    val mockPrefsEditor = mockk<SharedPreferences.Editor> {
      every { putLong(any(), any()) } returns this
      every { commit() } returns true
    }

    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockk {
      every { edit() } returns mockPrefsEditor
    }

    val mockContext = mockk<Context>(relaxed = true)
    val duration = TimeUnit.SECONDS.toMillis(1)
    val before = SystemClock.uptimeMillis() + duration
    PlaybackController.scheduleAutoStop(mockContext, duration)
    val after = SystemClock.uptimeMillis() + duration
    verify(exactly = 1) {
      mockPrefsEditor.putLong(PlaybackController.PREF_LAST_SCHEDULED_STOP_TIME, withArg {
        assertTrue(it in before..after)
      })

      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK, it.action)
          assertTrue(it.getLongExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, 0) in before..after)
        }
      )
    }
  }

  @Test
  fun testClearScheduledAutoStop() {
    val mockContext = mockk<Context>(relaxed = true)
    PlaybackController.clearScheduledAutoStop(mockContext)
    verify(exactly = 1) {
      mockContext.startService(
        withArg {
          assertEquals(MediaPlayerService::class.qualifiedName, it.component?.className)
          assertEquals(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK, it.action)

          val atUptimeMillis = it.getLongExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, 0)
          assertTrue(atUptimeMillis < SystemClock.uptimeMillis())
        }
      )
    }
  }

  @Test
  fun testGetScheduledAutoStopRemainingDurationMillis() {
    mockkStatic(PreferenceManager::class)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockk {
      every {
        getLong(PlaybackController.PREF_LAST_SCHEDULED_STOP_TIME, any())
      } returns SystemClock.uptimeMillis() + 1000L
    }

    val r = PlaybackController.getScheduledAutoStopRemainingDurationMillis(mockk(relaxed = true))
    assertTrue(r in 900..1000)
  }

  @Test
  fun testClearAutoStopCallback() {
    val handler = Handler(Looper.getMainLooper())
    PlaybackController.handleServiceIntent(
      mockk(),
      mockPlayerManager,
      Intent(PlaybackController.ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(PlaybackController.EXTRA_AT_UPTIME_MILLIS, SystemClock.uptimeMillis() + 1000),
      handler
    )

    PlaybackController.clearAutoStopCallback(handler)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify { mockPlayerManager wasNot called }
  }
}
