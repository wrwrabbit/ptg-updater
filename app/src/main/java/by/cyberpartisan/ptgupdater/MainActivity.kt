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
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
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


class MainActivity : AppCompatActivity() {
    enum class Step {
        COPY_FILES_FROM_OLD_TELEGRAM,
        UNINSTALL_OLD_APP,
        INSTALL_NEW_APP,
        COPY_FILES_TO_TELEGRAM,
        UNINSTALL_SELF
    }

    private var checkAppThread: Thread? = null

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
    private var step: Step
        get() = Step.valueOf(preferences.getString("step", "COPY_FILES")!!)
        set(x) { preferences.edit().putString("step", x.toString()).apply() }
    private lateinit var contract: TelegramActivityContract
    private lateinit var telegramLauncher: ActivityResultLauncher<ActivityInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)

        button = findViewById(R.id.button)
        button.setOnClickListener {
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

        intent.getByteArrayExtra("password")?.let { zipPassword = it }
        intent.getStringExtra("packageName")?.let { telegramPackageName = it }

        contract = TelegramActivityContract(fileToUri(File(filesDir, "received_files/data.zip")), zipPassword)
        telegramLauncher = registerForActivityResult(contract) { result ->
            if (result) {
                step = Step.UNINSTALL_SELF
                runOnUiThread {
                    updateUI()
                }
            }
        }

        if (checkAppThread == null) {
            checkAppThread = Thread{ checkApp() }
            checkAppThread?.start()
        }

        if (intent.data != null) {
            step = Step.COPY_FILES_FROM_OLD_TELEGRAM
            if (ContextCompat.checkSelfPermission( this, android.Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED ) {
                ActivityCompat.requestPermissions( this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            } else {
                receiveFiles()
            }
        } else {
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun receiveFiles() {
        Thread {
            runOnUiThread { progressBar.visibility = View.VISIBLE }
            val zipFile = File(filesDir, "full.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }
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
            }
            button.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
            button.visibility = View.VISIBLE
            if (step == Step.UNINSTALL_OLD_APP) {
                button.text = "Uninstall Old Telegram App"
            } else if (step == Step.INSTALL_NEW_APP) {
                button.text = "Install New Telegram App"
            } else if (step == Step.COPY_FILES_TO_TELEGRAM) {
                button.text = "Copy Files to Telegram"
            } else if (step == Step.UNINSTALL_SELF) {
                button.text = "Uninstall Updater"
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
        val uri = fileToUri(File(filesDir, "received_files/telegram.apk"))
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(intent)
    }

    private fun uninstallSelf() {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:" + packageName)
        startActivity(intent)
    }

    private fun checkApp() {
        while (true) {
            val updaterPackage = try {
                val pm: PackageManager = packageManager
                telegramPackageName?.let {
                    pm.getPackageInfo(it, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            if (step == Step.UNINSTALL_OLD_APP) {
                if (updaterPackage == null) {
                    step = Step.INSTALL_NEW_APP
                    runOnUiThread{ updateUI() }
                }
            } else if (step == Step.INSTALL_NEW_APP) {
                if (updaterPackage != null) {
                    step = Step.COPY_FILES_TO_TELEGRAM
                    runOnUiThread{ updateUI() }
                }
            } else if (step != Step.COPY_FILES_FROM_OLD_TELEGRAM) {
                break
            }
            Thread.sleep(100)
        }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                receiveFiles()
            }
        }
    }
}