package com.gps.map.camera.and.time.stamp.ui.rating

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.review.ReviewManagerFactory
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.localization.LocaleManager
import com.gps.map.camera.and.time.stamp.network.TrackerBackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RateAppActivity : AppCompatActivity() {
    private lateinit var entryPoint: RateAppManager.EntryPoint
    private var rating = 0
    private var submitting = false
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var illustration: ImageView
    private lateinit var stars: LinearLayout
    private lateinit var feedback: EditText
    private lateinit var count: TextView
    private lateinit var action: TextView
    private lateinit var later: TextView

    private lateinit var rate_feedback_container: RelativeLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        LocaleManager.applyPersisted(this)
        entryPoint = intent.getStringExtra(EXTRA_ENTRY_POINT)
            ?.let { value -> runCatching { RateAppManager.EntryPoint.valueOf(value) }.getOrNull() }
            ?: RateAppManager.EntryPoint.AUTOMATIC
        if (!RateAppManager.beginRateDialog(this, entryPoint)) {
            finish()
            return
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.activity_rate_app)
        title = findViewById(R.id.rate_title);
        subtitle = findViewById(R.id.rate_subtitle)
        illustration = findViewById(R.id.rate_illustration);
        stars = findViewById(R.id.rate_stars)
        feedback = findViewById(R.id.rate_feedback);
        count = findViewById(R.id.rate_count);
        action = findViewById(R.id.rate_action)
        later = findViewById(R.id.rate_later)
        rate_feedback_container = findViewById(R.id.rate_feedback_container)
        findViewById<View>(R.id.rate_close).setOnClickListener {
            RateAppManager.onRateDialogDismissed(); finish()
        }
        later.setOnClickListener {
            RateAppManager.onRateDialogDismissed()
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                RateAppManager.onRateDialogDismissed()
                finish()
            }
        })
        showStart()
        stars.visibility = View.VISIBLE;
        rate_feedback_container.visibility = View.GONE;
    }

    private fun showStart() {
        configure(R.drawable.ic_rate_intial, getString(R.string.rate_enjoying), getString(R.string.rate_feedback_help))
        renderStars();
        action.setText(R.string.rate_us)
        action.isEnabled = rating > 0
        action.setOnClickListener { if (rating > 0) showResult() }
        later.visibility = View.VISIBLE
        later.setTextColor(Color.parseColor("#A8CBF5"))
    }

    private fun showResult() {
        configure(
            if (rating <= 2) R.drawable.ic_rate_sorry else if (rating == 3) R.drawable.ic_rate_thanks else R.drawable.ic_rate_glad,
            if (rating <= 2) getString(R.string.rate_sorry) else if (rating == 3) getString(R.string.rate_feedback_thanks) else getString(R.string.rate_glad),
            if (rating <= 2) getString(R.string.rate_improve) else if (rating == 3) getString(R.string.rate_better) else getString(R.string.rate_google_prompt)
        )
        renderStars();
        action.text = getString(if (rating >= 4) R.string.rate_google else R.string.rate_give_feedback); action.isEnabled = true
        action.isEnabled = rating > 0
        action.setOnClickListener { if (rating >= 4) openPlay() else showFeedback() }
        later.visibility = View.VISIBLE
        later.setTextColor(Color.parseColor("#A8CBF5"))
    }

    private fun showFeedback() {
        configure(
            R.drawable.ic_rate_feedback,
            getString(R.string.rate_share_feedback),
            getString(R.string.rate_feedback_detail)
        )
        rate_feedback_container.visibility = View.VISIBLE;
        stars.visibility = View.GONE;
        later.visibility = View.GONE
        feedback.visibility = View.VISIBLE
        count.visibility = View.VISIBLE;
        count.text = getString(R.string.rate_character_count, feedback.length())
        action.setText(R.string.rate_submit)
        action.setOnClickListener { submitFeedback() }
        feedback.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                this@RateAppActivity.count.text = getString(R.string.rate_character_count, s?.length ?: 0)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun submitFeedback() {
        val text = feedback.text.toString().trim()
        if (text.isEmpty()) {
            feedback.error = getString(R.string.rate_feedback_required); return
        }
        if (submitting) return
        // Negative feedback is not a permanent rating. It only suppresses
        // another prompt until the next app session.
        RateAppManager.onNegativeFeedbackSubmitted()
        submitting = true; action.isEnabled = false; action.setText(R.string.rate_saving)
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                TrackerBackendClient(this@RateAppActivity).register(); TrackerBackendClient(
                this@RateAppActivity
            ).submitFeedback(rating, text) }
                .onSuccess {
                    Toast.makeText(this@RateAppActivity, R.string.rate_feedback_thanks, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .onFailure {
                    submitting = false; action.isEnabled = true; action.text =
                    getString(R.string.rate_submit); Toast.makeText(
                    this@RateAppActivity,
                    getString(R.string.rate_save_failed),
                    Toast.LENGTH_LONG
                ).show()
                }
        }
    }

    private fun configure(image: Int, heading: String, detail: String) {
        illustration.setImageResource(image); title.text = heading; subtitle.text = detail
        feedback.visibility = View.GONE; count.visibility = View.GONE; action.visibility =
            View.VISIBLE
    }

    private fun renderStars() {
        stars.removeAllViews()
        repeat(5) { index ->
            stars.addView(TextView(this).apply {
                text = if (index < rating) "★" else "☆"; textSize = 38f; gravity =
                Gravity.CENTER
                setTextColor(
                    if (index < rating) Color.parseColor("#FFD34E") else Color.parseColor("#A8CBF5")
                )
                setOnClickListener {
                    rating = index + 1
                    // First show the rating result. For 1–3 stars, the result
                    // screen exposes "Give Feedback", which opens the form.
                    showResult()
                }
            }, LinearLayout.LayoutParams(50.dp, 58.dp))
        }
    }

    private fun openPlay() {
        val id = packageName
        if (entryPoint == RateAppManager.EntryPoint.SETTINGS) {
            if (openPlayStore(id)) {
                RateAppManager.onUserRated(this)
            }
            return
        }
        RateAppManager.onUserRated(this)
        runCatching {
            val reviewManager = ReviewManagerFactory.create(this)
            reviewManager.requestReviewFlow().addOnSuccessListener { info ->
                reviewManager.launchReviewFlow(this, info).addOnCompleteListener { finish() }
            }.addOnFailureListener { finish() }
        }.onFailure { finish() }
    }

    private fun openPlayStore(id: String): Boolean {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$id")
        ).apply {
            // Prefer the Play Store app when it is installed.
            setPackage("com.android.vending")
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$id")
        )

        val launched = runCatching {
            if (marketIntent.resolveActivity(packageManager) != null) {
                startActivity(marketIntent)
            } else if (webIntent.resolveActivity(packageManager) != null) {
                startActivity(webIntent)
            } else {
                return@runCatching false
            }
            true
        }.getOrDefault(false)

        if (launched) {
            finish()
        } else {
            Toast.makeText(this, R.string.common_no_browser, Toast.LENGTH_SHORT).show()
        }
        return launched
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ENTRY_POINT = "rate_entry_point"
    }
}
