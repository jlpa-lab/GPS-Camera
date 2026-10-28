package com.mobile.myapplication.ui

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.ads.module.admob.Admob
import com.ads.module.ads.wrapper.ApInterstitialAd
import com.ads.module.funtion.AdCallback
import com.mobile.myapplication.R
import com.mobile.myapplication.localization.LocaleManager
import com.mobile.myapplication.pref.AppSharedPref
import com.mobile.myapplication.pref.AppSharedPreferencesApp
import javax.inject.Inject

abstract class BaseActivity<VB : ViewDataBinding> : AppCompatActivity() {

    companion object {
        private const val TAG = "BaseActivity"
    }

    lateinit var mBinding: VB

    @Inject
    lateinit var appSharedPref: AppSharedPref

    protected open val shouldShowNavigationBars = false

    private var loadingView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!::appSharedPref.isInitialized) {
            appSharedPref = AppSharedPreferencesApp(applicationContext)
        }
        setLocal()
        requestWindow()
        val layoutView = getLayoutActivity()
        mBinding = DataBindingUtil.setContentView(this, layoutView)
        Log.d(TAG, "onCreate: name Class: ${this::class.java.simpleName}")
        mBinding.lifecycleOwner = this
        registerBackPress()

//        initLoadingView()
        initViews()
        onResizeViews()
        onClickViews()
        observerData()
    }

    open fun setUpViews() {}

    abstract fun getLayoutActivity(): Int

    open fun requestWindow() {}

    open fun initViews() {}

    open fun onResizeViews() {}

    open fun onClickViews() {}

    open fun observerData() {}

    private fun setLocal() {
        LocaleManager.applyPersisted(this)
    }

    override fun onResume() {
        super.onResume()
        setUpSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setUpSystemBars()
    }

    private fun registerBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onActivityBackPressed()
            }
        })
    }

    open fun onActivityBackPressed() {
        finish()
    }

    private var lastBackPressedTime = 0L

    protected fun handleDoubleBackToExit(timeWindowMs: Long = 2000) {
        if (lastBackPressedTime + timeWindowMs > System.currentTimeMillis()) {
            finish()
        } else {
            lastBackPressedTime = System.currentTimeMillis()
            Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setUpSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { view, insets ->
            if (!shouldShowNavigationBars) {
                val controller = WindowInsetsControllerCompat(window, view)
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                view.updatePadding(bottom = view.resources.displayMetrics.density.toInt())
            }

            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)

            insets
        }
    }

    protected fun showInterNotCheckGap(interAd: ApInterstitialAd?, func: (() -> Unit)) {
        if (interAd != null) {
            Admob.getInstance()
                .forceShowInterstitial(this, interAd.interstitialAd, object : AdCallback() {
                    override fun onNextAction() {
                        super.onNextAction()
                        func.invoke()
                    }
                })
        } else func.invoke()
    }

    protected fun showLoading() {
        if (loadingView == null) {
            //initLoadingView()
        }
        loadingView?.isVisible = true
    }

    protected fun hideLoading() {
        loadingView?.isVisible = false
    }

//    private fun initLoadingView() {
//        val parent: ViewGroup = findViewById(R.id.content)
//        if (loadingView == null) {
//            loadingView = LayoutInflater.from(this).inflate(R.layout.view_loading, parent, false)
//            loadingView?.isVisible = false
//            parent.addView(loadingView)
//        }
//    }
}








