package com.aes.grammplayer.ui.features.settings
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.helper.ApplicationHelper
import com.aes.grammplayer.helper.StorageAutoManager
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

            val messagesPageSize = settingsDataStore.messagesPageSize.first()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_MESSAGES_PAGE_SIZE)
                    .title("Messages Loaded per Page:")
                    .description("$messagesPageSize")
                    .build()
            )

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_RESET_DEFAULT)
                    .title("Reset to Default")
                    .description("Restore default settings")
                    .build()
            )

            // Storage auto-manager
            val storageAutoDelete = settingsDataStore.storageAutoDelete.first()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_STORAGE_AUTO_DELETE)
                    .title("Auto-delete oldest")
                    .description(if (storageAutoDelete) "On" else "Off")
                    .build()
            )

            val storageMoveToSd = settingsDataStore.storageMoveToSd.first()
            val sdAvailable = ApplicationHelper.isExternalStorageAvailable()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_STORAGE_MOVE_TO_SD)
                    .title("Move to SD when available")
                    .description(if (!sdAvailable) "(no SD)" else if (storageMoveToSd) "On" else "Off")
                    .enabled(sdAvailable)
                    .build()
            )

            val threshold = settingsDataStore.storageThresholdMb.first()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_STORAGE_THRESHOLD)
                    .title("Low-space threshold")
                    .description("${threshold} MB")
                    .build()
            )

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_RUN_NOW)
                    .title("Run auto-clean now")
                    .description("Free space and move to SD")
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
            ACTION_ID_MESSAGES_PAGE_SIZE -> {
                lifecycleScope.launch {
                    val current = settingsDataStore.messagesPageSize.first()
                    // Cycle through fixed choices, wrapping back to the smallest.
                    val next = PAGE_SIZE_CHOICES[
                        (PAGE_SIZE_CHOICES.indexOf(current) + 1) % PAGE_SIZE_CHOICES.size
                    ]
                    settingsDataStore.setMessagesPageSize(next)
                    action.description = "$next"
                    notifyActionChanged(findActionPositionById(ACTION_ID_MESSAGES_PAGE_SIZE))
                }
            }
            ACTION_ID_RESET_DEFAULT -> {
                lifecycleScope.launch {
                    // Assuming default values: Auto Play = false (Off), Progress Threshold = 30%, Buffer Size = 300 MB
                    settingsDataStore.setAutoPlay(false)
                    settingsDataStore.setProgressThreshold(30)
                    settingsDataStore.setBufferSizeThreshold(300)
                    settingsDataStore.setMessagesPageSize(SettingsDataStore.DEFAULT_MESSAGES_PAGE_SIZE)
                    settingsDataStore.setStorageAutoDelete(true)
                    settingsDataStore.setStorageMoveToSd(false)
                    settingsDataStore.setStorageThresholdMb(500)
                    loadActions()
                }
            }
            ACTION_ID_STORAGE_AUTO_DELETE -> {
                lifecycleScope.launch {
                    val newVal = !settingsDataStore.storageAutoDelete.first()
                    settingsDataStore.setStorageAutoDelete(newVal)
                    loadActions()
                }
            }
            ACTION_ID_STORAGE_MOVE_TO_SD -> {
                if (!ApplicationHelper.isExternalStorageAvailable()) {
                    Toast.makeText(requireContext(), "No SD card available", Toast.LENGTH_SHORT).show()
                    return
                }
                lifecycleScope.launch {
                    val newVal = !settingsDataStore.storageMoveToSd.first()
                    settingsDataStore.setStorageMoveToSd(newVal)
                    loadActions()
                }
            }
            ACTION_ID_STORAGE_THRESHOLD -> {
                lifecycleScope.launch {
                    val current = settingsDataStore.storageThresholdMb.first()
                    val choices = listOf(300, 500, 1000)
                    val next = choices[(choices.indexOf(current) + 1) % choices.size]
                    // fallback if current not in choices (e.g., 200..2000) -> go to 300
                    val resolved = if (current !in choices) 300 else next
                    settingsDataStore.setStorageThresholdMb(resolved)
                    action.description = "${resolved} MB"
                    notifyActionChanged(findActionPositionById(ACTION_ID_STORAGE_THRESHOLD))
                }
            }
            ACTION_ID_RUN_NOW -> {
                lifecycleScope.launch {
                    val threshold = settingsDataStore.storageThresholdMb.first()
                    val moveToSd = settingsDataStore.storageMoveToSd.first()
                    val deleted = StorageAutoManager.ensureFreeSpace(requireContext(), threshold)
                    val moved = if (moveToSd) StorageAutoManager.moveToExternalIfPossible(requireContext()) else 0
                    Toast.makeText(requireContext(), "Cleaned $deleted files, moved $moved to SD", Toast.LENGTH_SHORT).show()
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
        private const val ACTION_ID_MESSAGES_PAGE_SIZE = 5L
        private const val ACTION_ID_STORAGE_AUTO_DELETE = 6L
        private const val ACTION_ID_STORAGE_MOVE_TO_SD = 7L
        private const val ACTION_ID_STORAGE_THRESHOLD = 8L
        private const val ACTION_ID_RUN_NOW = 9L

        private val PAGE_SIZE_CHOICES = listOf(25, 50, 100, 200)
    }
}