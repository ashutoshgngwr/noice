package com.github.ashutoshgngwr.noice.fragment

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.SoundManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_preset_list.view.*
import kotlinx.android.synthetic.main.layout_list_item__preset.view.*

class PresetFragment : Fragment(), SoundManager.OnPlaybackStateChangeListener {

  companion object {
    const val TAG = "PresetFragment"
  }

  private var mSoundManager: SoundManager? = null
  private var mRecyclerView: RecyclerView? = null

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val mServiceConnection = object : ServiceConnection {
    override fun onServiceDisconnected(name: ComponentName?) {
      Log.d(TAG, "MediaPlayerService disconnected")
      mSoundManager?.removeOnPlaybackStateChangeListener(this@PresetFragment)
      mSoundManager = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      service ?: return

      Log.d(TAG, "MediaPlayerService connected")
      if (service is MediaPlayerService.PlaybackBinder) {
        mSoundManager = service.getSoundManager().apply {
          addOnPlaybackStateChangeListener(this@PresetFragment)
        }
      }

      mRecyclerView?.adapter.apply {
        if (this is PresetListAdapter) {
          this.onPlaybackStateChanged()
        }
      }
    }
  }

  private val mAdapterDataObserver = object : RecyclerView.AdapterDataObserver() {
    override fun onChanged() {
      if (mRecyclerView?.adapter?.itemCount ?: 0 > 0) {
        requireView().indicator_list_empty.visibility = View.GONE
      } else {
        requireView().indicator_list_empty.visibility = View.VISIBLE
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requireContext().bindService(
      Intent(context, MediaPlayerService::class.java),
      mServiceConnection,
      Context.BIND_AUTO_CREATE
    )
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.fragment_preset_list, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    mRecyclerView = view.list_presets.apply {
      setHasFixedSize(true)
      adapter = PresetListAdapter(requireContext()).apply {
        registerAdapterDataObserver(mAdapterDataObserver)
      }
    }

    // manually call AdapterDataObserver#onChanged()
    mAdapterDataObserver.onChanged()
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    mRecyclerView?.adapter.apply {
      if (this is PresetListAdapter) {
        this.onPlaybackStateChanged()
      }
    }
  }

  override fun onDestroyView() {
    mRecyclerView?.adapter?.unregisterAdapterDataObserver(mAdapterDataObserver)
    super.onDestroyView()
  }

  override fun onDestroy() {
    requireContext().unbindService(mServiceConnection)

    // manually call onServiceDisconnected because framework does not
    // call it when service is intentionally unbound.
    mServiceConnection.onServiceDisconnected(null)
    super.onDestroy()
  }

  inner class PresetListAdapter(context: Context) : RecyclerView.Adapter<PresetListAdapter.ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)
    private val dataSet = ArrayList(Preset.readAllFromUserPreferences(context).asList())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(layoutInflater.inflate(R.layout.layout_list_item__preset, parent, false))
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      holder.itemView.title.text = dataSet[position].name
      holder.itemView.button_play.setImageResource(
        if (dataSet[position] == mSoundManager?.getCurrentPreset()) {
          R.drawable.ic_action_stop
        } else {
          R.drawable.ic_action_play
        }
      )
    }

    fun onPlaybackStateChanged() {
      val currentPreset = mSoundManager?.getCurrentPreset()
      dataSet.sortWith(
        compareByDescending<Preset> { it == currentPreset }
          .thenBy { it.name }
      )
      if (dataSet.isEmpty()) {
        notifyDataSetChanged()
      } else {
        notifyItemRangeChanged(0, dataSet.size)
      }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

      init {
        itemView.button_play.setOnClickListener {
          mSoundManager ?: return@setOnClickListener
          if (dataSet[adapterPosition] == mSoundManager?.getCurrentPreset()) {
            itemView.button_play.setImageResource(R.drawable.ic_action_play)
            mSoundManager?.stopPlayback()
          } else {
            itemView.button_play.setImageResource(R.drawable.ic_action_stop)
            mSoundManager?.playPreset(dataSet[adapterPosition])
          }
        }

        itemView.button_delete.setOnClickListener {
          AlertDialog.Builder(requireContext()).run {
            setMessage(getString(R.string.preset_delete_confirmation, dataSet[adapterPosition].name))
            setNegativeButton(R.string.cancel, null)
            setPositiveButton(R.string.delete) { _: DialogInterface, _: Int ->
              val preset = dataSet.removeAt(adapterPosition)
              Preset.writeAllToUserPreferences(context, dataSet.toTypedArray())
              if (preset == mSoundManager?.getCurrentPreset()) {
                mSoundManager?.stopPlayback() // will onPlaybackStateChanged()
              } else {
                onPlaybackStateChanged() // otherwise explicitly
              }

              @Suppress("DEPRECATION")
              Snackbar.make(requireView(), R.string.preset_deleted, Snackbar.LENGTH_LONG)
                .setBackgroundTint(resources.getColor(R.color.colorPrimary))
                .setAction(R.string.dismiss) { }
                .show()
            }
            show()
          }
        }
      }
    }
  }

  data class Preset(var name: String, val playbackStates: Array<PresetPlaybackState>) {

    data class PresetPlaybackState(val soundKey: String, val volume: Float, val timePeriod: Int)

    init {
      playbackStates.sortBy { T -> T.soundKey }
    }

    companion object {
      fun readAllFromUserPreferences(context: Context): Array<Preset> {
        Gson().also { gson ->
          return gson.fromJson(
            PreferenceManager.getDefaultSharedPreferences(context).getString("presets", "[]"),
            Array<Preset>::class.java
          )
        }
      }

      fun writeAllToUserPreferences(context: Context, presets: Array<Preset>) {
        PreferenceManager.getDefaultSharedPreferences(context).also { sharedPreferences ->
          sharedPreferences.edit()
            .putString("presets", Gson().toJson(presets))
            .apply()
        }
      }

      fun appendToUserPreferences(context: Context, preset: Preset) {
        val presetList = ArrayList(readAllFromUserPreferences(context).asList())
        presetList.add(preset)
        writeAllToUserPreferences(context, presetList.toTypedArray())
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) {
        return true
      }

      if (javaClass != other?.javaClass) {
        return false
      }

      // name need not be equal. playbackStates should be
      other as Preset
      if (!playbackStates.contentEquals(other.playbackStates)) {
        return false
      }

      return true
    }

    override fun hashCode(): Int {
      // auto-generated
      var result = name.hashCode()
      result = 31 * result + playbackStates.contentHashCode()
      return result
    }
  }
}
