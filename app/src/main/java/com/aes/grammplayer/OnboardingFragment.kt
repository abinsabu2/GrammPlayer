package com.aes.grammplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.leanback.app.OnboardingSupportFragment
import com.aes.grammplayer.db.view.SettingsViewModel

class OnboardingFragment : OnboardingSupportFragment() {

    private val viewModel: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }

    private val pageTitles = arrayOf("Welcome To The GramPlayer", "Discover Latest Movies!", "Enjoy Your Favorites")
    private val pageDescriptions = arrayOf(
        "The Ultimate Movie Experience For Your TV.",
        "Explore A Vast Library Of Movies And Curated Playlists.",
        "Create Your Own Playlists And Watch The Movies You Love."
    )
    private val pageImages = intArrayOf(
        R.drawable.ic_music_note,
        R.drawable.ic_album,
        R.drawable.ic_playlist
    )

    override fun getPageCount(): Int = pageTitles.size

    override fun getPageTitle(pageIndex: Int): CharSequence = pageTitles[pageIndex]

    override fun getPageDescription(pageIndex: Int): CharSequence = pageDescriptions[pageIndex]

    override fun onCreateBackgroundView(inflater: LayoutInflater, container: ViewGroup?): View? = null

    override fun onCreateContentView(inflater: LayoutInflater, container: ViewGroup?): View? {
        val content = inflater.inflate(R.layout.onboarding_content, container, false)
        val imageView = content.findViewById<ImageView>(R.id.onboarding_image)
        imageView.setImageResource(pageImages[currentPageIndex])
        return content
    }

    override fun onCreateForegroundView(inflater: LayoutInflater, container: ViewGroup?): View? {
        return inflater.inflate(R.layout.onboarding_custom_controls, container, false)
    }

    override fun onPageChanged(newPage: Int, previousPage: Int) {
        super.onPageChanged(newPage, previousPage)
        val imageView = view?.findViewById<ImageView>(R.id.onboarding_image)
        imageView?.setImageResource(pageImages[newPage])
    }

    override fun onFinishFragment() {
        super.onFinishFragment()
        viewModel.updateOnboarding()
        val intent = Intent(requireActivity(), TermsActivity::class.java)
        startActivity(intent)
    }

    override fun onProvideTheme(): Int {
        return androidx.leanback.R.style.Theme_Leanback_Onboarding
    }
}