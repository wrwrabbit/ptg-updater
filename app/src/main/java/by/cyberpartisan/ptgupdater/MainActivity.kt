package by.cyberpartisan.ptgupdater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private lateinit var contentTextView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contentTextView = findViewById(R.id.content_text_view)

        if (intent.data != null) {
            if (ContextCompat.checkSelfPermission( this, android.Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED ) {
                ActivityCompat.requestPermissions( this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            } else {
                processZip()
            }
        }
    }

    fun processZip() {
        Thread {
            val zipFile = File(filesDir, "data.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }
            val inputStream: InputStream? = if (Build.VERSION.SDK_INT >= 24) {
                getContentResolver().openInputStream(intent.data!!)
            } else {
                FileInputStream(intent.data!!.toFile())
            }
            val outputStream = openFileOutput("data.zip", Context.MODE_PRIVATE)
            IOUtils.copy(inputStream, outputStream)
            inputStream?.close()
            outputStream.flush()
            outputStream.close()

            val password = intent.getStringExtra("password")
            val zip = ZipFile(zipFile, password?.toCharArray())
            val configFile = File(filesDir, "userconfig1.xml")
            if (configFile.exists()) {
                configFile.delete()
            }
            zip.extractFile("shared_prefs/userconfig1.xml", filesDir.absolutePath, "userconfig1.xml")
            zip.close()
            val text = configFile.readText()
            runOnUiThread {
                contentTextView.text = text
                contentTextView.movementMethod = ScrollingMovementMethod()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                processZip()
            }
        }
    }
}