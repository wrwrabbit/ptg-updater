package by.cyberpartisan.ptgupdater

import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private lateinit var contentTextView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contentTextView = findViewById(R.id.content_text_view)

        if (intent.data != null) {
            Thread {
                val zipFile = File(filesDir, "data.zip")
                if (zipFile.exists()) {
                    zipFile.delete()
                }
                val inputStream: InputStream? = getContentResolver().openInputStream(intent.data!!)
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
    }
}