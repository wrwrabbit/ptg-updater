package by.cyberpartisan.ptgupdater

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.DisplayMetrics
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*


class MainActivity : AppCompatActivity() {
    enum class Step {
        COPY_FILES_FROM_OLD_TELEGRAM,
        UNINSTALL_OLD_APP,
        INSTALL_NEW_APP,
        COPY_FILES_TO_TELEGRAM,
        UNINSTALL_SELF
    }

    private var checkAppThread: Thread? = null

    private lateinit var stepTextView: TextView
    private lateinit var descriptionTextView: TextView

    private lateinit var button: Button
    private lateinit var progressBar: ProgressBar
    private val preferences
        get() = getSharedPreferences("config", MODE_PRIVATE)
    private var zipPassword: ByteArray?
        get() = preferences.getString("zipPassword", null)?.let { Base64.decode(it, Base64.DEFAULT) }
        set(x) { preferences.edit().putString("zipPassword", Base64.encodeToString(x, Base64.DEFAULT)).apply() }
    private var telegramPackageName: String?
        get() = preferences.getString("telegramPackageName", null)
        set(x) { preferences.edit().putString("telegramPackageName", x).apply() }
    private var dataUri: Uri?
        get() = preferences.getString("dataUri", null)?.let { Uri.parse(it) }
        set(x) { preferences.edit().putString("dataUri", x.toString()).apply() }
    private var newTelegramUri: Uri?
        get() = preferences.getString("newTelegramUri", null)?.let { Uri.parse(it) }
        set(x) { preferences.edit().putString("newTelegramUri", x.toString()).apply() }
    private var step: Step
        get() = Step.valueOf(preferences.getString("step", "COPY_FILES_FROM_OLD_TELEGRAM")!!)
        set(x) { preferences.edit().putString("step", x.toString()).apply() }
    private var localeOverride: String?
        get() = preferences.getString("localeOverride", null)
        set(x) { preferences.edit().putString("localeOverride", x).apply() }
    private var contract: TelegramActivityContract? = null
    private lateinit var telegramLauncher: ActivityResultLauncher<ActivityInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViews()

        intent.getByteArrayExtra("password")?.let { zipPassword = it }
        intent.getStringExtra("packageName")?.let { telegramPackageName = it }

        createTread()
        if (intent.data != null) {
            saveIntentData()
        } else {
            updateUI()
        }
        localeOverride?.let { setLocale(it) }
        createTelegramLauncher()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun receiveFiles() {
        Thread {
            runOnUiThread { progressBar.visibility = View.VISIBLE }
            val zipFile = getFileAndDeleteIfExists("full.zip")
            copyFileFromTelegram(intent.data!!, "full.zip")

            val dir = File(filesDir, "received_files")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            dir.mkdir()

            val zip = ZipFile(zipFile)
            val telegramFile = File(filesDir, "telegram.apk")
            if (telegramFile.exists()) {
                telegramFile.delete()
            }
            zip.extractFile("telegram.apk", dir.absolutePath)
            val dataFile = File(filesDir, "data.zip")
            if (dataFile.exists()) {
                dataFile.delete()
            }
            zip.extractFile("data.zip", dir.absolutePath)
            zip.close()
            zipFile.delete()
            step = Step.UNINSTALL_OLD_APP
            runOnUiThread {
                progressBar.visibility = View.GONE
                updateUI()
            }
        }.start()
    }

    private fun copyFileFromTelegram(from: Uri, to: String) {
        val toFile = File(filesDir, to)
        if (toFile.exists()) {
            toFile.delete()
        }
        val inputStream: InputStream? = if (Build.VERSION.SDK_INT >= 24) {
            contentResolver.openInputStream(from)
        } else {
            FileInputStream(from.toFile())
        }
        val outputStream = openFileOutput(to, Context.MODE_PRIVATE)
        IOUtils.copy(inputStream, outputStream)
        inputStream?.close()
        outputStream.close()
    }

    private fun updateUI() {
        if (step == Step.COPY_FILES_FROM_OLD_TELEGRAM) {
            if (intent.data != null) {
                progressBar.visibility = View.VISIBLE
                descriptionTextView.text = ""
            } else {
                progressBar.visibility = View.GONE
                descriptionTextView.text = resources.getString(R.string.back_to_ptr_description)
            }
            stepTextView.text = ""
            button.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
            button.visibility = View.VISIBLE
            if (step == Step.UNINSTALL_OLD_APP) {
                stepTextView.text = resources.getString(R.string.step4)
                descriptionTextView.text = resources.getString(R.string.uninstall_old_telegram_app_description)
                button.text = resources.getString(R.string.uninstall_old_telegram_app)
            } else if (step == Step.INSTALL_NEW_APP) {
                stepTextView.text = resources.getString(R.string.step5)
                descriptionTextView.text = resources.getString(R.string.install_new_telegram_app_description)
                button.text = resources.getString(R.string.install_new_telegram_app)
            } else if (step == Step.COPY_FILES_TO_TELEGRAM) {
                stepTextView.text = resources.getString(R.string.step6)
                descriptionTextView.text = resources.getString(R.string.transfer_files_to_telegram_description)
                button.text = resources.getString(R.string.transfer_files_to_telegram)
            } else if (step == Step.UNINSTALL_SELF) {
                stepTextView.text = resources.getString(R.string.step7)
                descriptionTextView.text = resources.getString(R.string.uninstall_updater_description)
                button.text = resources.getString(R.string.uninstall_updater)
            }
        }
    }

    private fun uninstallOldApp() {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:" + telegramPackageName)
        startActivity(intent)
    }

    private fun installNewApp() {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            fileToUri(File(filesDir, "received_files/telegram.apk"))
        } else {
            newTelegramUri
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(intent)
    }

    private fun uninstallSelf() {
        deleteFileByUri(dataUri)
        deleteFileByUri(newTelegramUri)

        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    private fun checkApp() {
        while (true) {
            if (step == Step.UNINSTALL_OLD_APP) {
                if (!isTelegramInstalled()) {
                    step = Step.INSTALL_NEW_APP
                    runOnUiThread{ updateUI() }
                }
            } else if (step == Step.INSTALL_NEW_APP) {
                if (isTelegramInstalled()) {
                    step = Step.COPY_FILES_TO_TELEGRAM
                    runOnUiThread{ updateUI() }
                }
            } else if (step == Step.COPY_FILES_FROM_OLD_TELEGRAM) {
                if (!isTelegramInstalled()) {
                    step = Step.INSTALL_NEW_APP
                    runOnUiThread{ updateUI() }
                }
            } else {
                break
            }
            Thread.sleep(100)
        }
    }

    private fun isTelegramInstalled(): Boolean {
        val telegramPackage = try {
            val pm: PackageManager = packageManager
            telegramPackageName?.let {
                pm.getPackageInfo(it, 0)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return telegramPackage != null
    }

    private fun findTelegramActivity(): ActivityInfo? {
        val searchIntent = Intent(Intent.ACTION_MAIN)
        searchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val infoList: List<ResolveInfo> = packageManager.queryIntentActivities(searchIntent, 0)
        for (info in infoList) {
            if (info.activityInfo.packageName == telegramPackageName) {
                return info.activityInfo
            }
        }
        return null
    }

    private fun fileToUri(file: File): Uri? {
        return if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file)
        } else {
            Uri.fromFile(file)
        }
    }

    @Suppress("DEPRECATION")
    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        val resources = resources
        val configuration = resources.getConfiguration()
        val displayMetrics: DisplayMetrics = resources.getDisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale)
        } else {
            configuration.locale = locale
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            applicationContext.createConfigurationContext(configuration)
        } else {
            resources.updateConfiguration(configuration, displayMetrics)
        }
    }

    private fun findViews() {
        stepTextView = findViewById(R.id.step)
        descriptionTextView = findViewById(R.id.description)

        progressBar = findViewById(R.id.progressBar)

        button = findViewById(R.id.button)
        button.setOnClickListener { onClickButton() }
    }

    private fun createTread() {
        if (checkAppThread == null) {
            checkAppThread = Thread{ checkApp() }
            checkAppThread?.start()
        }
    }

    private fun saveIntentData() {
        if (Build.VERSION.SDK_INT >= 24) {
            step = Step.COPY_FILES_FROM_OLD_TELEGRAM
            if (ContextCompat.checkSelfPermission( this, android.Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED ) {
                ActivityCompat.requestPermissions( this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            } else {
                receiveFiles()
            }
        } else {
            dataUri = intent.data
            intent.getParcelableExtra<Uri>("telegramApk")?.let { newTelegramUri = it }
            step = Step.UNINSTALL_OLD_APP
            updateUI()
            contract = TelegramActivityContract(dataUri, zipPassword)
        }
        localeOverride = intent.getStringExtra("language")
    }

    private fun onClickButton() {
        if (step == Step.UNINSTALL_OLD_APP) {
            uninstallOldApp()
        } else if (step == Step.INSTALL_NEW_APP) {
            installNewApp()
        } else if (step == Step.COPY_FILES_TO_TELEGRAM) {
            findTelegramActivity()?.let {
                try {
                    telegramLauncher.launch(it)
                } catch (ignored: Exception) {
                }
            }
        } else if (step == Step.UNINSTALL_SELF) {
            uninstallSelf()
        }
    }

    private fun createTelegramLauncher() {
        if (Build.VERSION.SDK_INT >= 24) {
            contract = TelegramActivityContract(fileToUri(File(filesDir, "received_files/data.zip")), zipPassword)
        } else if (dataUri != null) {
            contract = TelegramActivityContract(dataUri, zipPassword)
        }
        contract?.let {
            telegramLauncher = registerForActivityResult(it) { result ->
                if (result) {
                    step = Step.UNINSTALL_SELF
                    runOnUiThread {
                        updateUI()
                    }
                }
            }
        }
    }

    private fun getFileAndDeleteIfExists(name: String): File {
        val file = File(filesDir, name)
        if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
        return file
    }

    private fun deleteFileByUri(uri: Uri?) {
        uri?.let { u ->
            u.path?.let { path ->
                File(path).delete()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                receiveFiles()
            }
        }
    }
}