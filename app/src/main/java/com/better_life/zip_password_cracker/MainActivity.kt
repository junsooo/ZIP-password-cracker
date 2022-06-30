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
        // 배너 광고 코드
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

        // 전면 광고
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
                // TODO: 서버로 invalid한지 보내기?
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
                            // TODO: 이거 초반에 해야할
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
                // 없는 경우 알려줘야 함
                Toast.makeText(this, this.getString(R.string.no_password_found), Toast.LENGTH_LONG).show()
            }
        }
        // 퍼미션 요청: https://webnautes.tistory.com/1225 참고함
        val readPerm = ContextCompat.checkSelfPermission(this, requiredPermission)
        if (readPerm != PackageManager.PERMISSION_GRANTED) {
            // 이전에 퍼미션 거부를 한적이 있는가?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
                // 요청을 진행하기 전에 사용자에게 퍼미션이 필요한 이유를 설명
                val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_AppCompat_Light_Dialog))
                builder.setTitle(this.getString(R.string.permission_needed))
                builder.setMessage(this.getString(R.string.permission_needed_content))
                builder.setPositiveButton(this.getString(R.string.ok)) { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(requiredPermission), PERMISSION_REQUEST_CODE)
                }
                builder.setCancelable(false)
                builder.show()
            } else {
                // 퍼미션 거부를 한적이 없으면 바로 퍼미션 요청
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
        // 거부한 퍼미션이 있으면 앱을 사용할 수 없는 이유를 설명하고 앱을 종료
        if (!checkResult) {
            val bundle = Bundle()
            firebaseAnalytics.logEvent("permission_denied", bundle)
            val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_AppCompat_Light_Dialog))
            builder.setTitle(this.getString(R.string.permission_needed))
            // 거부만 선택한 경우에는 앱을 다시 실행해 허용을 선택하면 앱 사용 가능
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
                builder.setMessage(this.getString(R.string.permission_denied_restart))
            } else {
                // 다시 묻지 않음을 사용자가 체크하고 거부한 경우
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

    // 현재 selectZipButton의 결과를 가져오는데 사용
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == BUTTON_INTENT_CODE && resultCode == RESULT_OK) {
            // TODO: FileUtils 가져오는거 버전들 여러개, sd card에서 테스트해야할듯
            val realZipPath = FileUtils.getPath(this, data?.data)
            val zipPath: EditText = findViewById(R.id.zip_file_path)
            val startButton: Button = findViewById(R.id.start_button)
            // 체크 중복 로직 제거? checkAndGetZipFile
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
                    val `is`: InputStream = zipFile.getInputStream(fileHeaders[0]) // fileHeader 없는경우 처리? 폴더는?
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