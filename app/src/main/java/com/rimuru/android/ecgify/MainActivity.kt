package com.rimuru.android.ecgify

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.rimuru.android.ecgify.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var gestureDetector: GestureDetector

    private val fragmentOrder = listOf(
        R.id.homeFragment,
        R.id.resultsFragment,
        R.id.historyFragment,
        R.id.aiFragment
    )

    companion object {
        // Флаг для отслеживания инициализации OpenCV
        var isOpenCVInitialized = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Инициализируем OpenCV
        initOpenCV()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setupGestureDetector()
        setupBottomNavigation()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment -> binding.toolbar.title = "ECGify"
                R.id.resultsFragment -> binding.toolbar.title = "Results"
                R.id.historyFragment -> binding.toolbar.title = "History"
                R.id.aiFragment -> binding.toolbar.title = "AI Analysis"
            }
            binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            insets
        }
    }

    private fun initOpenCV() {
        // Простая синхронная инициализация OpenCV
        try {
            val success = OpenCVLoader.initDebug()
            if (success) {
                isOpenCVInitialized = true
                println("OpenCV инициализирован успешно")
            } else {
                println("OpenCV не удалось инициализировать")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Ошибка при инициализации OpenCV: ${e.message}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val SWIPE_THRESHOLD = 0
                val SWIPE_VELOCITY_THRESHOLD = 0

                try {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x

                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            val currentId = navController.currentDestination?.id ?: return false
                            val currentIndex = fragmentOrder.indexOf(currentId)

                            return if (diffX > 0) {
                                if (currentIndex > 0) {
                                    navigateToFragment(fragmentOrder[currentIndex - 1], isForward = false)
                                    true
                                } else false
                            } else {
                                if (currentIndex < fragmentOrder.size - 1) {
                                    navigateToFragment(fragmentOrder[currentIndex + 1], isForward = true)
                                    true
                                } else false
                            }
                        }
                    }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
                return false
            }
        })

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val currentId = navController.currentDestination?.id
            val targetId = item.itemId

            if (currentId == targetId) return@setOnItemSelectedListener true

            val currentIndex = fragmentOrder.indexOf(currentId)
            val targetIndex = fragmentOrder.indexOf(targetId)

            val isForward = targetIndex > currentIndex
            navigateToFragment(targetId, isForward)
            true
        }

        val currentId = navController.currentDestination?.id ?: R.id.homeFragment
        binding.bottomNav.menu.findItem(currentId)?.isChecked = true
    }

    private fun navigateToFragment(fragmentId: Int, isForward: Boolean) {
        val navOptions = NavOptions.Builder().apply {
            if (isForward) {
                setEnterAnim(R.anim.slide_in_right)
                setExitAnim(R.anim.slide_out_left)
                setPopEnterAnim(R.anim.slide_in_left)
                setPopExitAnim(R.anim.slide_out_right)
            } else {
                setEnterAnim(R.anim.slide_in_left)
                setExitAnim(R.anim.slide_out_right)
                setPopEnterAnim(R.anim.slide_in_right)
                setPopExitAnim(R.anim.slide_out_left)
            }
        }.build()

        navController.navigate(fragmentId, null, navOptions)
    }
}