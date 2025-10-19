package com.aes.grammplayer

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.graphics.drawable.Drawable
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

/**
 * A fragment that shows a detailed text view of a MediaMessage and its metadata.
 */
class VideoDetailsFragment : DetailsSupportFragment() {

    private var mSelectedMovie: MediaMessage? = null
    private lateinit var mDetailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var mAdapter: ArrayObjectAdapter

    // Define a constant for the Download action ID
    companion object {
        private const val TAG = "VideoDetailsFragment"
        private const val ACTION_DOWNLOAD = 1L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate DetailsFragment")
        super.onCreate(savedInstanceState)

        mDetailsBackground = DetailsSupportFragmentBackgroundController(this)

        // Safely get the MediaMessage object from the intent
        mSelectedMovie = activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as? MediaMessage

        // If the object is null, finish the activity to prevent a crash
        if (mSelectedMovie == null) {
            Log.e(TAG, "MediaMessage is null. Closing activity.")
            activity?.finish()
            return
        }

        setupUI()
        initializeBackground(mSelectedMovie)
    }

    private fun setupUI() {
        // This presenter will display the text details using our DetailsDescriptionPresenter
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())

        // Set the background color for the details row
        detailsPresenter.backgroundColor = ContextCompat.getColor(requireContext(), R.color.selected_background)

        // Set up the listener for the "Download" button
        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            if (action.id == ACTION_DOWNLOAD) {
                handleDownloadAction()
            } else {
                Toast.makeText(requireContext(), action.toString(), Toast.LENGTH_SHORT).show()
            }
        }

        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)

        mAdapter = ArrayObjectAdapter(presenterSelector)
        adapter = mAdapter

        // Create the main details row and add it to the adapter
        val detailsRow = DetailsOverviewRow(mSelectedMovie)

        // --- NO IMAGE IS SET ON THE ROW ---
        // The row will now only contain the text details and actions.

        // Add the download action to the row
        val actionAdapter = ArrayObjectAdapter()
        actionAdapter.add(Action(ACTION_DOWNLOAD, "Download"))
        detailsRow.actionsAdapter = actionAdapter

        mAdapter.add(detailsRow)
    }

    private fun handleDownloadAction() {
        val fileId = mSelectedMovie?.fileId
        if (fileId != null && fileId != 0) {
            Toast.makeText(requireContext(), "Starting download...", Toast.LENGTH_SHORT).show()
            //TelegramClientManager.startFileDownload(fileId)
        } else {
            Toast.makeText(requireContext(), "Error: No valid file ID to download.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeBackground(movie: MediaMessage?) {
        mDetailsBackground.enableParallax()
        Glide.with(this)
            .asBitmap()
            .centerCrop()
            .error(R.drawable.default_background)
            .load(movie?.backgroundImageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    bitmap: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    mDetailsBackground.coverBitmap = bitmap
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    // Clear the background if the resource is cleared
                    mDetailsBackground.coverBitmap = null
                }
            })
    }
}
