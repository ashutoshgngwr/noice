package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.media.AudioManager
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 28])
class MediaPlayerServiceTest {

  private lateinit var serviceController: ServiceController<MediaPlayerService>
  private lateinit var binder: MediaPlayerService.PlaybackBinder

  @Before
  fun setup() {
    serviceController = Robolectric.buildService(MediaPlayerService::class.java).create()
    binder = serviceController.get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder
  }

  @Test
  fun `should not be in foreground if playback is not started`() {
    serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder
    assert(shadowOf(serviceController.get()).lastForegroundNotification == null)
  }

  @Test
  fun `should come to foreground on media playback`() {
    binder.getSoundManager().play(LIBRARY[0].key)
    assert(shadowOf(serviceController.get()).lastForegroundNotificationId == MediaPlayerService.FOREGROUND_ID)
  }

  @Test
  fun `should not be in foreground if playback is stopped`() {
    binder.getSoundManager().play(LIBRARY[0].key)
    binder.getSoundManager().stopPlayback()

    assert(shadowOf(serviceController.get()).isForegroundStopped)
  }

  @Test
  fun `should send pause playback intent on notification pause action click`() {
    binder.getSoundManager().play(LIBRARY[0].key)

    val contentIntent = shadowOf(
      shadowOf(serviceController.get())
        .lastForegroundNotification
        .actions[1]
        .actionIntent
    ).savedIntent

    assert(contentIntent.getIntExtra("action", 0) == MediaPlayerService.RC_STOP_PLAYBACK)
  }

  @Test
  fun `should send resume playback intent on notification play action click`() {
    binder.getSoundManager().play(LIBRARY[0].key)
    binder.getSoundManager().pausePlayback()

    val contentIntent = shadowOf(
      shadowOf(serviceController.get())
        .lastForegroundNotification
        .actions[1]
        .actionIntent
    ).savedIntent

    assert(contentIntent.getIntExtra("action", 0) == MediaPlayerService.RC_START_PLAYBACK)
  }

  @Test
  fun `should send stop service intent on notification stop action click`() {
    binder.getSoundManager().play(LIBRARY[0].key)

    val contentIntent = shadowOf(
      shadowOf(serviceController.get())
        .lastForegroundNotification
        .actions[0]
        .actionIntent
    ).savedIntent

    assert(contentIntent.getIntExtra("action", 0) == MediaPlayerService.RC_STOP_SERVICE)
  }

  @Test
  fun `should resume playback on receiving start playback intent`() {
    serviceController = Robolectric.buildService(
      MediaPlayerService::class.java,
      Shadow.newInstanceOf(Intent::class.java)
        .putExtra("action", MediaPlayerService.RC_START_PLAYBACK)
    ).create()

    val binder = serviceController.get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(LIBRARY[0].key)
    binder.getSoundManager().pausePlayback()
    serviceController.startCommand(0, 0)
    assert(!binder.getSoundManager().isPaused() && binder.getSoundManager().isPlaying)
  }

  @Test
  fun `should not resume playback on receiving start playback intent if focus request was delayed`() {
    serviceController = Robolectric.buildService(
      MediaPlayerService::class.java,
      Shadow.newInstanceOf(Intent::class.java)
        .putExtra("action", MediaPlayerService.RC_START_PLAYBACK)
    ).create()

    val binder = serviceController.get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(LIBRARY[0].key)
    serviceController.get().handleAudioFocusRequestResult(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
    serviceController.startCommand(0, 0)
    assert(binder.getSoundManager().isPaused() && !binder.getSoundManager().isPlaying)
  }

  @Test
  fun `should pause playback on receiving stop playback intent`() {
    serviceController = Robolectric.buildService(
      MediaPlayerService::class.java,
      Shadow.newInstanceOf(Intent::class.java)
        .putExtra("action", MediaPlayerService.RC_STOP_PLAYBACK)
    ).create()

    val binder = serviceController.get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(LIBRARY[0].key)
    serviceController.startCommand(0, 0)
    assert(binder.getSoundManager().isPaused() && !binder.getSoundManager().isPlaying)
  }

  @Test
  fun `should exit foreground on receiving stop service intent`() {
    serviceController = Robolectric.buildService(
      MediaPlayerService::class.java,
      Shadow.newInstanceOf(Intent::class.java)
        .putExtra("action", MediaPlayerService.RC_STOP_SERVICE)
    ).create()

    val binder = serviceController.get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(LIBRARY[0].key)
    serviceController.startCommand(0, 0)
    assert(!binder.getSoundManager().isPlaying)
    assert(shadowOf(serviceController.get()).isForegroundStopped)
  }

  @Test
  fun `should pause playback on receiving becoming noisy broadcast`() {
    val mSoundPoolShadow = shadowOf(binder.getSoundManager().mSoundPool)
    binder.getSoundManager().play(LIBRARY[3].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && binder.getSoundManager().isPlaying
    )

    mSoundPoolShadow.clearPlayed()
    RuntimeEnvironment
      .systemContext
      .sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    assert(
      !mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && binder.getSoundManager().isPaused()
    )
  }

  @Test
  fun `should not do anything on receiving becoming noisy broadcast if playback is not playing`() {
    assert(!binder.getSoundManager().isPlaying)

    RuntimeEnvironment
      .systemContext
      .sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

    assert(!binder.getSoundManager().isPaused())
  }

  @Test
  fun `should stop playback on audio focus request failure`() {
    binder.getSoundManager().play(LIBRARY[0].key)
    assert(binder.getSoundManager().isPlaying)

    serviceController.get().handleAudioFocusRequestResult(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
    assert(!binder.getSoundManager().isPlaying && !binder.getSoundManager().isPaused())
  }

  @Test
  fun `should start delayed playback on getting focus after a focus request is delayed`() {
    val mSoundPoolShadow = shadowOf(binder.getSoundManager().mSoundPool)
    binder.getSoundManager().play(LIBRARY[0].key)
    mSoundPoolShadow.clearPlayed()
    serviceController.get().handleAudioFocusRequestResult(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
        && binder.getSoundManager().isPlaying
    )
  }

  @Test
  fun `should pause playback on permanent audio focus loss`() {
    val mSoundPoolShadow = shadowOf(binder.getSoundManager().mSoundPool)
    binder.getSoundManager().play(LIBRARY[0].key)
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
        && binder.getSoundManager().isPlaying
    )

    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    assert(!binder.getSoundManager().isPlaying && binder.getSoundManager().isPaused())
  }

  @Test
  fun `should resume playback after getting back audio focus`() {
    binder.getSoundManager().play(LIBRARY[0].key)
    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
    assert(binder.getSoundManager().isPaused())

    val mSoundPoolShadow = shadowOf(binder.getSoundManager().mSoundPool)
    mSoundPoolShadow.clearPlayed()
    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
        && binder.getSoundManager().isPlaying
    )
  }

  @Test
  fun `should pause playback on temporarily losing focus`() {
    binder.getSoundManager().play(LIBRARY[0].key)
    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
    assert(binder.getSoundManager().isPaused())
  }

  @Test
  fun `should not fail on destroy`() {
    serviceController.create().destroy()
  }
}
