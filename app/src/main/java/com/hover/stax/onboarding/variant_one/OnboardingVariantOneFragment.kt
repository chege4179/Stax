package com.hover.stax.onboarding.variant_one

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.hover.stax.databinding.OnboardingVariantOneBinding
import com.hover.stax.databinding.OnboardingVariantOneSlideBinding

class OnboardingVariantOneFragment : Fragment(), ViewPager.OnPageChangeListener {

	private var _binding: OnboardingVariantOneBinding? = null
	private val binding get() = _binding!!

	private lateinit var viewPagerAdapter: SlidesPagerAdapter
	private lateinit var slidesBinding: OnboardingVariantOneSlideBinding

	override fun onCreateView(inflater: LayoutInflater,
	                          container: ViewGroup?,
	                          savedInstanceState: Bundle?): View {
		_binding = OnboardingVariantOneBinding.inflate(inflater, container, false)
		slidesBinding = OnboardingVariantOneSlideBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setUpSlides()
	}

	private fun setUpSlides() {
		val viewPagerAdapter = SlidesPagerAdapter(requireContext(), slidesBinding)
		val viewPager = binding.vpPager
		viewPager.apply {
			startAutoScroll(FIRST_SCROLL_DELAY)
			setInterval(OTHER_SCROLL_DELAY)
			setCycle(true)
			setAutoScrollDurationFactor(AUTO_SCROLL_EASE_DURATION_FACTOR)
			setSwipeScrollDurationFactor(SWIPE_DURATION_FACTOR)
			setStopScrollWhenTouch(true)
			addOnPageChangeListener(this@OnboardingVariantOneFragment)
			adapter = viewPagerAdapter
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}


	companion object {
		const val FIRST_SCROLL_DELAY = 4000
		const val OTHER_SCROLL_DELAY = 5000L
		const val SWIPE_DURATION_FACTOR = 2.0
		const val AUTO_SCROLL_EASE_DURATION_FACTOR = 5.0
	}

	override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
		TODO("Not yet implemented")
	}

	override fun onPageSelected(position: Int) {
		TODO("Not yet implemented")
	}

	override fun onPageScrollStateChanged(state: Int) {
		TODO("Not yet implemented")
	}

}