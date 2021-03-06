package com.better_life.zip_password_cracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.*
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.exception.ZipException.Type
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.io.IOException
import java.io.InputStream

private lateinit var firebaseAnalytics: FirebaseAnalytics

const val BUTTON_INTENT_CODE = 1
const val PERMISSION_REQUEST_CODE = 1

const val INVALID_ZIP_CODE = 1
const val NOT_ENCRYPTED_ZIP_CODE = 2

const val password_file = "pass.txt"

val requiredPermission = android.Manifest.permission.READ_EXTERNAL_STORAGE


class MainActivity : AppCompatActivity() {
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    private val adSize: AdSize
        get() {
            val display = windowManager.defaultDisplay
            val outMetrics = DisplayMetrics()
            display.getMetrics(outMetrics)

            val density = outMetrics.density

            var adWidthPixels = outMetrics.widthPixels.toFloat()

            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.share -> {
                val bundle = Bundle()
                firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, bundle)

                val shareText = this.getString(R.string.app_name) + "\nhttps://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID
                val shareIntent: Intent = ShareCompat.IntentBuilder.from(this)
                    .setType("text/plain")
                    .setText(shareText)
                    .intent
                if (shareIntent.resolveActivity(packageManager) != null) {
                    startActivity(shareIntent)
                }
            }
            R.id.license -> {
                startActivity(Intent(this, OssLicensesMenuActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mainLayout: LinearLayout = findViewById(R.id.ads_layout)
        // ?????? ?????? ??????
        /*
        MobileAds.initialize(this) {}
        val mAdView = AdView(this)
        mAdView.adUnitId = bannerAdUnitId
        mAdView.adSize = adSize
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        mainLayout.addView(mAdView, params)

        // ?????? ??????
        val mInterstitialAd = InterstitialAd(this)
        mInterstitialAd.adUnitId = interstitialAdUnitId
        mInterstitialAd.loadAd(AdRequest.Builder().build())
        mInterstitialAd.adListener = object : AdListener() {
            override fun onAdClosed() {
                mInterstitialAd.loadAd(AdRequest.Builder().build())
            }
        }
        */

        val selectZipButton: Button = findViewById(R.id.select_zip_file)
        selectZipButton.setOnClickListener {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "ZIP")
            firebaseAnalytics.logEvent("select_zip_file", bundle)

            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/zip"
            startActivityForResult(intent, BUTTON_INTENT_CODE)
        }

        var startButton: Button = findViewById(R.id.start_button)
        startButton.setOnClickListener {
            if(mInterstitialAd.isLoaded) {
                mInterstitialAd.show()
            }

            val zipPath: EditText = findViewById(R.id.zip_file_path)
            val (zipFile, success, code) = checkAndGetZipFile(zipPath.text.toString())
            if(!success) {
                if(code == INVALID_ZIP_CODE) {
                    Toast.makeText(this, this.getString(R.string.zip_invalid), Toast.LENGTH_LONG).show()
                } else if(code == NOT_ENCRYPTED_ZIP_CODE) {
                    Toast.makeText(this, this.getString(R.string.zip_not_encrypted), Toast.LENGTH_LONG).show()
                }
                zipPath.setText("")
                startButton.visibility = View.INVISIBLE
                val bundle = Bundle()
                firebaseAnalytics.logEvent("zip_password_find_start_fail", bundle)
                // TODO: ????????? invalid?????? ??????????
                return@setOnClickListener
            }
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, zipPath.text.toString())
            firebaseAnalytics.logEvent("zip_password_find_start", bundle)

            application.assets.open(password_file).bufferedReader().useLines{lines ->
                lines.forEach {
                    val succes = verify(zipFile, it)
                    if(succes) {
                        bundle.putString("password", it)
                        firebaseAnalytics.logEvent("zip_password_find_success", bundle)

                        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_AppCompat_Light_Dialog))
                        builder.setTitle(this.getString(R.string.password_found))
                        builder.setMessage(this.getString(R.string.password) + ": $it")
                        builder.setNeutralButton(this.getString(R.string.copy_button)) { _, _ ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ZIP password", it)
                            // TODO: ?????? ????????? ?????????
                            clipboard.addPrimaryClipChangedListener {
                                Toast.makeText(this, this.getString(R.string.clipboard_copy), Toast.LENGTH_LONG).show()
                            }
                            clipboard.setPrimaryClip(clip)
                        }
                        builder.setNegativeButton(this.getString(R.string.close_button)) { _, _ ->

                        }
                        builder.show()

                        return@setOnClickListener
                    }
                }
                firebaseAnalytics.logEvent("zip_password_find_fail", bundle)
                // ?????? ?????? ???????????? ???
                Toast.makeText(this, this.getString(R.string.no_password_found), Toast.LENGTH_LONG).show()
            }
        }
        // ????????? ??????: https://webnautes.tistory.com/1225 ?????????
        val readPerm = ContextCompat.checkSelfPermission(this, requiredPermission)
        if (readPerm != PackageManager.PERMISSION_GRANTED) {
            // ????????? ????????? ????????? ????????? ??????????
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
                // ????????? ???????????? ?????? ??????????????? ???????????? ????????? ????????? ??????
                val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_AppCompat_Light_Dialog))
                builder.setTitle(this.getString(R.string.permission_needed))
                builder.setMessage(this.getString(R.string.permission_needed_content))
                builder.setPositiveButton(this.getString(R.string.ok)) { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(requiredPermission), PERMISSION_REQUEST_CODE)
                }
                builder.setCancelable(false)
                builder.show()
            } else {
                // ????????? ????????? ????????? ????????? ?????? ????????? ??????
                ActivityCompat.requestPermissions(this, arrayOf(requiredPermission), PERMISSION_REQUEST_CODE)
            }
        }

        firebaseAnalytics = Firebase.analytics
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        var checkResult = true
        if(requestCode == PERMISSION_REQUEST_CODE && grantResults.size == 1) {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                checkResult = false
            }
        }
        // ????????? ???????????? ????????? ?????? ????????? ??? ?????? ????????? ???????????? ?????? ??????
        if (!checkResult) {
            val bundle = Bundle()
            firebaseAnalytics.logEvent("permission_denied", bundle)
            val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_AppCompat_Light_Dialog))
            builder.setTitle(this.getString(R.string.permission_needed))
            // ????????? ????????? ???????????? ?????? ?????? ????????? ????????? ???????????? ??? ?????? ??????
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
                builder.setMessage(this.getString(R.string.permission_denied_restart))
            } else {
                // ?????? ?????? ????????? ???????????? ???????????? ????????? ??????
                builder.setMessage(this.getString(R.string.permission_denied_settings))
            }
            builder.setPositiveButton(this.getString(R.string.ok)) { _, _ ->
                /*
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
                */
                finish()
            }
            builder.setCancelable(false)
            builder.show()
        }
        val bundle = Bundle()
        firebaseAnalytics.logEvent("permission_success", bundle)
        return super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // ?????? selectZipButton??? ????????? ??????????????? ??????
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == BUTTON_INTENT_CODE && resultCode == RESULT_OK) {
            // TODO: FileUtils ??????????????? ????????? ?????????, sd card?????? ?????????????????????
            val realZipPath = FileUtils.getPath(this, data?.data)
            val zipPath: EditText = findViewById(R.id.zip_file_path)
            val startButton: Button = findViewById(R.id.start_button)
            // ?????? ?????? ?????? ??????? checkAndGetZipFile
            val (_, success, code) = checkAndGetZipFile(realZipPath)
            if(!success) {
                val bundle = Bundle()
                if(code == INVALID_ZIP_CODE) {
                    bundle.putString("code", INVALID_ZIP_CODE.toString())
                    Toast.makeText(this, this.getString(R.string.zip_invalid), Toast.LENGTH_LONG).show()
                } else if(code == NOT_ENCRYPTED_ZIP_CODE) {
                    bundle.putString("code", NOT_ENCRYPTED_ZIP_CODE.toString())
                    Toast.makeText(this, this.getString(R.string.zip_not_encrypted), Toast.LENGTH_LONG).show()
                }
                bundle.putInt("version", Build.VERSION.SDK_INT)
                zipPath.setText("")
                startButton.visibility = View.INVISIBLE
                firebaseAnalytics.logEvent("select_zip_file_fail", bundle)
            } else {
                val bundle = Bundle()
                firebaseAnalytics.logEvent("select_zip_file_success", bundle)
                zipPath.setText(realZipPath)
                zipPath.setSelection(realZipPath.length)
                startButton.visibility = View.VISIBLE
            }
        }
    }

    private fun checkAndGetZipFile(path: String): Triple<ZipFile, Boolean, Int> {
        val zipFile = ZipFile(File(path))
        if (!zipFile.isValidZipFile) {
            return Triple(zipFile, false, INVALID_ZIP_CODE)
        }
        if (!zipFile.isEncrypted) {
            return Triple(zipFile, false, NOT_ENCRYPTED_ZIP_CODE)
        }
        return Triple(zipFile, true, 0)
    }

    private fun verify(zipFile: ZipFile, password: String): Boolean {
        try {
            zipFile.setPassword(password.toCharArray())
            val fileHeaders: List<FileHeader> = zipFile.fileHeaders
                try {
                    val `is`: InputStream = zipFile.getInputStream(fileHeaders[0]) // fileHeader ???????????? ??????? ??????????
                    val b = ByteArray(4 * 4096)
                    while (`is`.read(b) != -1) {
                        //Do nothing as we just want to verify password
                    }
                    `is`.close()
                    return true
                } catch (e: ZipException) {
                    return if (e.type === Type.WRONG_PASSWORD) {
                        Log.d("tag","wrong password")
                        false
                    } else {
                        val bundle = Bundle()
                        firebaseAnalytics.logEvent("zip_file_verify_corrupt_file", bundle)
                        //Corrupt file
                        e.printStackTrace()
                        false
                    }
                } catch (e: IOException) {
                    println("Most probably wrong password.")
                    return false
                }
        } catch (e: Exception) {
            val bundle = Bundle()
            firebaseAnalytics.logEvent("zip_file_verify_other_exception", bundle)
            println("Some other exception occurred")
            e.printStackTrace()
            return false
        }
    }
}