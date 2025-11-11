package com.aes.grammplayer
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min

class SettingsFragment : GuidedStepSupportFragment() {

    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsDataStore = SettingsDataStore(requireActivity())
        super.onCreate(savedInstanceState)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Settings",
            "Adjust your preferences",
            "",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        loadActions()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun loadActions() {
        lifecycleScope.launch {
            val newActions = mutableListOf<GuidedAction>()
            val isAutoPlay = settingsDataStore.autoPlay.first()

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_AUTO_PLAY)
                    .title("Auto Play")
                    .description(if (isAutoPlay) "On" else "Off")
                    .build()
            )

            if (isAutoPlay) {
                val progressThreshold = settingsDataStore.progressThreshold.first()
                newActions.add(
                    GuidedAction.Builder(requireContext())
                        .id(ACTION_ID_PROGRESS_THRESHOLD)
                        .title("Start when Download Progress Reaches:")
                        .description("$progressThreshold%")
                        .build()
                )

                val bufferSizeThreshold = settingsDataStore.bufferSizeThreshold.first()
                newActions.add(
                    GuidedAction.Builder(requireContext())
                        .id(ACTION_ID_BUFFER_SIZE_THRESHOLD)
                        .title("Start when Download Buffer Size Reaches:")
                        .description("$bufferSizeThreshold MB")
                        .build()
                )
            }

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_RESET_DEFAULT)
                    .title("Reset to Default")
                    .description("Restore default settings")
                    .build()
            )

            setActions(newActions)
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_ID_AUTO_PLAY -> {
                lifecycleScope.launch {
                    val newAutoPlay = !settingsDataStore.autoPlay.first()
                    settingsDataStore.setAutoPlay(newAutoPlay)
                    loadActions()
                }
            }
            ACTION_ID_PROGRESS_THRESHOLD -> {
                // For simplicity, this example just increments the threshold by 10.
                // A more complete implementation would show a sub-step to enter a value.
                lifecycleScope.launch {
                    val currentProgressThreshold = settingsDataStore.progressThreshold.first()
                    val newProgressThreshold = min(currentProgressThreshold + 10, 100)
                    settingsDataStore.setProgressThreshold(newProgressThreshold)
                    action.description = "$newProgressThreshold%"
                    notifyActionChanged(findActionPositionById(ACTION_ID_PROGRESS_THRESHOLD))
                }
            }
            ACTION_ID_BUFFER_SIZE_THRESHOLD -> {
                // For simplicity, this example just increments the threshold by 50.
                // A more complete implementation would show a sub-step to enter a value.
                lifecycleScope.launch {
                    val newBufferSizeThreshold = settingsDataStore.bufferSizeThreshold.first() + 50
                    settingsDataStore.setBufferSizeThreshold(newBufferSizeThreshold)
                    action.description = "$newBufferSizeThreshold MB"
                    notifyActionChanged(findActionPositionById(ACTION_ID_BUFFER_SIZE_THRESHOLD))
                }
            }
            ACTION_ID_RESET_DEFAULT -> {
                lifecycleScope.launch {
                    // Assuming default values: Auto Play = false (Off), Progress Threshold = 30%, Buffer Size = 300 MB
                    settingsDataStore.setAutoPlay(false)
                    settingsDataStore.setProgressThreshold(30)
                    settingsDataStore.setBufferSizeThreshold(300)
                    loadActions()
                }
            }
        }
    }

    companion object {
        private const val ACTION_ID_AUTO_PLAY = 1L
        private const val ACTION_ID_PROGRESS_THRESHOLD = 2L
        private const val ACTION_ID_BUFFER_SIZE_THRESHOLD = 3L
        private const val ACTION_ID_RESET_DEFAULT = 4L
    }
}