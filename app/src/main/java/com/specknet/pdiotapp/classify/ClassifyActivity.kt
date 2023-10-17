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
import com.specknet.pdiotapp.ml.TestModel
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClassifyActivity: AppCompatActivity() {

    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    lateinit var lastThreeSecondsData: ArrayList<Array<Float>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classify)

//        val result: TextView = findViewById(R.id.displayText)
//        val button: Button = findViewById(R.id.button)

//        button.setOnClickListener {
//            textView.text = "Something else"
//        }

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
            classifyActivity(lastThreeSecondsData)
            lastThreeSecondsData.clear()
        }
        lastThreeSecondsData.add(dataPoint)
    }

    fun classifyActivity(data: ArrayList<Array<Float>>) {
        try {
            val context = this
            val model = TestModel.newInstance(context)

            // Creates inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 75, 6), DataType.FLOAT32)
            val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*75*6)
            byteBuffer.order(ByteOrder.nativeOrder())

            for (array in data) {
                for (value in array) {
                    byteBuffer.putFloat(value)
                }
            }

            inputFeature0.loadBuffer(byteBuffer)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray // could be .getFloatArray() instead

            var maxPosition = 0
            var maxConfidence: Float = 0F

            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPosition = i
                }
            }

            val CLASSES = arrayOf(
                "misc_movements&normal_breathing",
                "sitting&singing",
                "standing&talking",
                "sitting&normal_breathing",
                "standing&laughing",
                "lying_down_back&talking",
                "standing&normal_breathing",
                "lying_down_back&coughing",
                "standing&singing",
                "shuffle_walking&normal_breathing",
                "descending_stairs&normal_breathing",
                "sitting&eating",
                "standing&coughing",
                "lying_down_stomach&normal_breathing",
                "lying_down_stomach&talking",
                "lying_down_left&hyperventilating",
                "sitting&hyperventilating",
                "lying_down_back&singing",
                "lying_down_right&hyperventilating",
                "walking&normal_breathing",
                "sitting&coughing",
                "sitting&talking",
                "lying_down_right&coughing",
                "lying_down_stomach&hyperventilating",
                "lying_down_left&normal_breathing",
                "standing&hyperventilating",
                "lying_down_stomach&laughing",
                "lying_down_left&coughing",
                "standing&eating",
                "running&normal_breathing",
                "lying_down_stomach&singing",
                "lying_down_back&hyperventilating",
                "lying_down_back&normal_breathing",
                "lying_down_right&normal_breathing",
                "lying_down_left&laughing",
                "lying_down_left&talking",
                "ascending_stairs&normal_breathing",
                "lying_down_right&laughing",
                "lying_down_right&singing",
                "lying_down_right&talking",
                "lying_down_back&laughing",
                "sitting&laughing",
                "lying_down_stomach&coughing",
                "lying_down_left&singing"
            )

            runOnUiThread {
                val result: TextView = findViewById(R.id.displayText)
                result.text = CLASSES[maxPosition]
            }

            // Releases model resources if no longer used.
            model.close()
        } catch (e: Exception) {
            Log.e("Classifier Error", "An error occurred: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        looperRespeck.quit()
    }
}