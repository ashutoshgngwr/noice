package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SleepTimerFragmentBinding
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.google.android.material.snackbar.Snackbar

class SleepTimerFragment : Fragment() {

  private lateinit var binding: SleepTimerFragmentBinding
  private lateinit var analyticsProvider: AnalyticsProvider

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = SleepTimerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    analyticsProvider = NoiceApplication.of(requireContext()).getAnalyticsProvider()
    binding.durationPicker.setResetButtonEnabled(false)
    binding.durationPicker.setOnDurationAddedListener(this::onDurationAdded)

    val duration = PlaybackController.getScheduledAutoStopRemainingDurationMillis(requireContext())
    if (duration > 0) {
      binding.countdownView.startCountdown(duration)
    }

    val params = bundleOf("is_scheduled" to (duration > 0))
    analyticsProvider.setCurrentScreen("sleep_timer", SleepTimerFragment::class, params)
  }

  override fun onDestroyView() {
    val duration = PlaybackController.getScheduledAutoStopRemainingDurationMillis(requireContext())
    if (duration > 0) {
      analyticsProvider.logEvent("set_sleep_timer", bundleOf("duration_ms" to duration))
    }

    super.onDestroyView()
  }

  private fun onDurationAdded(duration: Long) {
    var remaining = 0L
    var enableResetButton = false
    if (duration < 0) { // duration picker reset
      PlaybackController.clearScheduledAutoStop(requireContext())
      Snackbar.make(requireView(), R.string.auto_sleep_schedule_cancelled, Snackbar.LENGTH_SHORT)
        .show()
    } else {
      remaining = PlaybackController.getScheduledAutoStopRemainingDurationMillis(requireContext())
      remaining += duration
      PlaybackController.scheduleAutoStop(requireContext(), remaining)
      enableResetButton = true
    }

    binding.durationPicker.setResetButtonEnabled(enableResetButton)
    binding.countdownView.startCountdown(remaining)
    // maybe show in-app review dialog to the user
    NoiceApplication.of(requireContext())
      .getReviewFlowProvider()
      .maybeAskForReview(requireActivity())
  }
}
