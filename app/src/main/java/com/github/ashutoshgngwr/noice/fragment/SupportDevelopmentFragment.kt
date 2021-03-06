package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ShareCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SupportDevelopmentFragmentBinding
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider

class SupportDevelopmentFragment : Fragment() {

  private lateinit var binding: SupportDevelopmentFragmentBinding
  private lateinit var analyticsProvider: AnalyticsProvider

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = SupportDevelopmentFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    analyticsProvider = NoiceApplication.of(requireContext()).getAnalyticsProvider()

    binding.shareButton.setOnClickListener {
      val text = getString(R.string.app_description)
      val targetURL = getString(R.string.support_development__share_url)
      ShareCompat.IntentBuilder(requireActivity())
        .setChooserTitle(R.string.support_development__share)
        .setType("text/plain")
        .setText("$text\n\n$targetURL")
        .startChooser()

      analyticsProvider.logEvent("share_app_with_friends", bundleOf())
    }

    analyticsProvider.setCurrentScreen("support_development", SupportDevelopmentFragment::class)
  }
}
