package com.specknet.pdiotapp.classify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.PostRequestSender
import com.specknet.pdiotapp.utils.RESpeckLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

class ClassifyActivity: AppCompatActivity() {

    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    lateinit var lastThreeSecondsData: ArrayList<Array<Float>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classify)

        val textView: TextView = findViewById(R.id.displayText)
        val button: Button = findViewById(R.id.button)

        button.setOnClickListener {
            textView.text = "Something else"
        }

        setupDataList()

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // Get acceleration data
                    val accelX = liveData.accelX
                    val accelY = liveData.accelY
                    val accelZ = liveData.accelZ

                    // Get gyroscope data
                    val gyroscopeReading = liveData.gyro
                    val gyroX = gyroscopeReading.x
                    val gyroY = gyroscopeReading.y
                    val gyroZ = gyroscopeReading.z

                    val dataPoint = arrayOf(accelX, accelY, accelZ, gyroX, gyroY, gyroZ)
                    updateData(dataPoint)
                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)
    }

    private fun setupDataList() {
        lastThreeSecondsData = ArrayList<Array<Float>>()
    }

    fun updateData(dataPoint: Array<Float>) {
        if (lastThreeSecondsData.size == 75) {
//            val sender: PostRequestSender = PostRequestSender(lastThreeSecondsData)
//            val classification = sender.makeRequest()
            lastThreeSecondsData.clear()
//            val textView: TextView = findViewById(R.id.displayText)
//            textView.text = "Classification: $classification"
//            makeRequest()
        }
        lastThreeSecondsData.add(dataPoint)
    }

    fun makeRequest() {
        val sender = PostRequestSender(lastThreeSecondsData)

        // Perform network request in a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val classification = sender.performNetworkRequest()

                // Update UI on the main thread
                runOnUiThread {
                    val textView: TextView = findViewById(R.id.displayText)
                    textView.text = "Classification: $classification"
                }
            } catch (e: IOException) {
                // Handle network-related errors here
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        looperRespeck.quit()
    }
}