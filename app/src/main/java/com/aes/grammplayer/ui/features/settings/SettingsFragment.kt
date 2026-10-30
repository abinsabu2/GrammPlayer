package com.aes.grammplayer.ui.features.settings
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.ApplicationHelper
import com.aes.grammplayer.helper.LocaleHelper
import com.aes.grammplayer.helper.StorageAutoManager
import com.aes.grammplayer.ui.features.dashboard.MainActivity
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
            getString(R.string.settings_title),
            getString(R.string.settings_subtitle),
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

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_LANGUAGE)
                    .title(getString(R.string.settings_language))
                    .description(LocaleHelper.currentLanguage(requireContext()).nativeName)
                    .multilineDescription(true)
                    .build()
            )
            val isAutoPlay = settingsDataStore.autoPlay.first()

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_AUTO_PLAY)
                    .title(getString(R.string.settings_auto_play))
                    .description(onOff(isAutoPlay))
                    .multilineDescription(true)
                    .build()
            )

            if (isAutoPlay) {
                val progressThreshold = settingsDataStore.progressThreshold.first()
                newActions.add(
                    GuidedAction.Builder(requireContext())
                        .id(ACTION_ID_PROGRESS_THRESHOLD)
                        .title(getString(R.string.settings_progress_threshold))
                        .description("$progressThreshold%")
                        .multilineDescription(true)
                    .build()
                )

                val bufferSizeThreshold = settingsDataStore.bufferSizeThreshold.first()
                newActions.add(
                    GuidedAction.Builder(requireContext())
                        .id(ACTION_ID_BUFFER_SIZE_THRESHOLD)
                        .title(getString(R.string.settings_buffer_threshold))
                        .description("$bufferSizeThreshold MB")
                        .multilineDescription(true)
                    .build()
                )
            }

            val messagesPageSize = settingsDataStore.messagesPageSize.first()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_MESSAGES_PAGE_SIZE)
                    .title(getString(R.string.settings_messages_page_size))
                    .description("$messagesPageSize")
                    .multilineDescription(true)
                    .build()
            )

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_RESET_DEFAULT)
                    .title(getString(R.string.settings_reset))
                    .description(getString(R.string.settings_reset_description))
                    .multilineDescription(true)
                    .build()
            )

            // Storage auto-manager
            val storageAutoDelete = settingsDataStore.storageAutoDelete.first()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_STORAGE_AUTO_DELETE)
                    .title(getString(R.string.settings_auto_delete))
                    .description(onOff(storageAutoDelete))
                    .multilineDescription(true)
                    .build()
            )

            val storageMoveToSd = settingsDataStore.storageMoveToSd.first()
            val sdAvailable = ApplicationHelper.isExternalStorageAvailable()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_STORAGE_MOVE_TO_SD)
                    .title(getString(R.string.settings_move_to_sd))
                    .description(if (!sdAvailable) getString(R.string.settings_no_sd) else onOff(storageMoveToSd))
                    .enabled(sdAvailable)
                    .multilineDescription(true)
                    .build()
            )

            val threshold = settingsDataStore.storageThresholdMb.first()
            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_STORAGE_THRESHOLD)
                    .title(getString(R.string.settings_low_space_threshold))
                    .description("${threshold} MB")
                    .multilineDescription(true)
                    .build()
            )

            newActions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_RUN_NOW)
                    .title(getString(R.string.settings_run_clean))
                    .description(getString(R.string.settings_run_clean_description))
                    .multilineDescription(true)
                    .build()
            )

            setActions(newActions)
        }
    }

    private fun onOff(enabled: Boolean): String =
        getString(if (enabled) R.string.settings_on else R.string.settings_off)

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_ID_LANGUAGE -> {
                LocaleHelper.showPicker(requireActivity()) {
                    // Rebuild the whole task so every screen picks up the new language.
                    startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
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
                    Toast.makeText(requireContext(), R.string.settings_no_sd_toast, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), getString(R.string.settings_clean_result, deleted, moved), Toast.LENGTH_SHORT).show()
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
        private const val ACTION_ID_LANGUAGE = 10L

        private val PAGE_SIZE_CHOICES = listOf(25, 50, 100, 200)
    }
}