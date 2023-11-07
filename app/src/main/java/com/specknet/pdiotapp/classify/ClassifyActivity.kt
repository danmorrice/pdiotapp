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
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.specknet.pdiotapp.MainActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.bluetooth.ConnectingActivity
import com.specknet.pdiotapp.live.LiveDataActivity
import com.specknet.pdiotapp.ml.Cnn2
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClassifyActivity: AppCompatActivity() {

    private lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    private lateinit var looperRespeck: Looper
    private val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    private lateinit var nonGyroDataStream: ArrayList<Array<Float>>
    private lateinit var gyroDataStream: ArrayList<Array<Float>>

    private var nonGyroDataPointCounter = 0
    private var gyroDataPointCounter = 0

    private var noOfSecondsForModel = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classify)

        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigator)
        bottomNavigationView.selectedItemId = R.id.classify_page

        setupDataStreams()

        val gyroSwitch: Switch = findViewById(R.id.gyroSwitch)
        val gyroSwitchText: TextView = findViewById(R.id.gyroSwitchText)

        streamDataToModel(false)

        gyroSwitch.setOnCheckedChangeListener { _, isChecked ->
            streamDataToModel(isChecked)
            if (isChecked) {
                gyroSwitchText.setText("Using gyroscope readings")
            } else {
                gyroSwitchText.setText("Not using gyroscope readings")
            }
        }


        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home_page -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.live_data_page -> {
                    val intent = Intent(this, LiveDataActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.classify_page -> {
                    val intent = Intent(this, ClassifyActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.setup_page -> {
                    val intent = Intent(this, ConnectingActivity::class.java)
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)
    }

    private fun setupDataStreams() {
        nonGyroDataStream = ArrayList()
        gyroDataStream = ArrayList()
    }

    private fun streamDataToModel(isGyroOn: Boolean) {
        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("Classify activity", "Running on thread: " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // Get acceleration data
                    val accelX = liveData.accelX
                    val accelY = liveData.accelY
                    val accelZ = liveData.accelZ

                    val dataPoint: Array<Float>

                    if (!isGyroOn) {
                        // If we switch to non gyro data, clear the gyro stream so it is empty when
                        // we return to it
                        gyroDataStream.clear()
                        gyroDataPointCounter = 0

                        dataPoint = arrayOf(accelX, accelY, accelZ)
                    } else {
                        // If we switch to gyro data, clear the non gyro stream so it is empty when
                        // we return to it
                        nonGyroDataStream.clear()
                        nonGyroDataPointCounter = 0

                        // Get gyroscope data
                        val gyroscopeReading = liveData.gyro
                        val gyroX = gyroscopeReading.x
                        val gyroY = gyroscopeReading.y
                        val gyroZ = gyroscopeReading.z

                        dataPoint = arrayOf(accelX, accelY, accelZ, gyroX, gyroY, gyroZ)
                    }
                    updateData(dataPoint, false)
                }
            }
        }
    }


    fun updateData(dataPoint: Array<Float>, isGyroOn: Boolean) {
        val dataStream: ArrayList<Array<Float>>
        if (isGyroOn) {
            dataStream = gyroDataStream
        } else {
            dataStream = nonGyroDataStream
        }

        // Add the new data point to the end of the ArrayList
        dataStream.add(dataPoint)

        // If the ArrayList is full, remove the first element
        if (dataStream.size == 25*noOfSecondsForModel) {
            dataStream.removeAt(0)
        }

        // Classify activity after every 12 data points are read in (~0.5 seconds)
        if (isGyroOn) {
            gyroDataPointCounter++
            if (gyroDataPointCounter == 12) {
                classifyNonGyroActivity(dataStream)
                gyroDataPointCounter = 0
            }
        } else {
            nonGyroDataPointCounter++
            if (nonGyroDataPointCounter == 12) {
                classifyNonGyroActivity(dataStream)
                nonGyroDataPointCounter = 0
            }
        }
    }

    private fun classifyNonGyroActivity(data: ArrayList<Array<Float>>) {
        try {
            val context = this
            val model = Cnn2.newInstance(context)

            // Creates inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 4*noOfSecondsForModel, 3), DataType.FLOAT32)
            val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*4*noOfSecondsForModel*3)
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

            val confidences = outputFeature0.floatArray

            var maxPosition = 0
            var maxConfidence = 0F

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

            val result: TextView = findViewById(R.id.classificationText)
            result.setText(CLASSES[maxPosition])

            // Releases model resources if no longer used.
            model.close()
        } catch (e: Exception) {
            Log.e("CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    private fun classifyGyroActivity(data: ArrayList<Array<Float>>) {
        try {
            val context = this
            val model = Cnn2.newInstance(context)

            // Creates inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 4*noOfSecondsForModel, 6), DataType.FLOAT32)
            val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*4*noOfSecondsForModel*6)
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
            var maxConfidence = 0F

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

            val result: TextView = findViewById(R.id.classificationText)
            result.setText(CLASSES[maxPosition])

            // Releases model resources if no longer used.
            model.close()
        } catch (e: Exception) {
            Log.e("CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        looperRespeck.quit()
    }
}