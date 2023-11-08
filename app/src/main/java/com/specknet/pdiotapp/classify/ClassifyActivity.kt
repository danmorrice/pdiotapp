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
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

import com.specknet.pdiotapp.ml.StationaryOrMoving
import com.specknet.pdiotapp.ml.MovingClassifier
import com.specknet.pdiotapp.ml.StationaryPositionClassifier
import com.specknet.pdiotapp.ml.LyingDownBackModel
import com.specknet.pdiotapp.ml.LyingDownStomachModel
import com.specknet.pdiotapp.ml.LyingDownLeftModel
import com.specknet.pdiotapp.ml.LyingDownRightModel
import com.specknet.pdiotapp.ml.SittingStandingModel


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
        val inputFeatures: TensorBuffer

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
                gyroDataPointCounter = 0
                inputFeatures = prepareDataForClassification(dataStream, 6)
                // TODO: Use input features for gyroscope data
            }
        } else {
            nonGyroDataPointCounter++
            if (nonGyroDataPointCounter == 12) {
                nonGyroDataPointCounter = 0
                inputFeatures = prepareDataForClassification(dataStream, 3)
                classifyNonGyroActivity(inputFeatures)
            }
        }
    }

    private fun prepareDataForClassification(data: ArrayList<Array<Float>>, dataWidth: Int): TensorBuffer {
        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 4*noOfSecondsForModel, dataWidth), DataType.FLOAT32)
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*4*noOfSecondsForModel*dataWidth)
        byteBuffer.order(ByteOrder.nativeOrder())
        for (array in data) {
            for (value in array) {
                byteBuffer.putFloat(value)
            }
        }
        inputFeature0.loadBuffer(byteBuffer)
        return inputFeature0
    }

    private fun classifyNonGyroActivity(inputFeatures: TensorBuffer) {
        try {
            // TODO: Deal with case binary classifier returns -1 (failure)
            val binaryClassification = stationaryOrMovingClassifier(inputFeatures)

            val result: TextView = findViewById(R.id.classificationText)
            result.setText(result.toString())

//            if (binaryClassification == 1) {
//                // Prediction is moving
//                movingClassifier(inputFeatures)
//                return
//            }
//
//            // Else prediction is moving
//            val stationaryPosition = stationaryPositionClassifier(inputFeatures)
//            if (stationaryPosition == 0) {
//                // Sitting or standing
//                sittingOrStandingClassifier(inputFeatures)
//            } else if (stationaryPosition == 1) {
//                // Lying down on back
//                lyingBackClassifier(inputFeatures)
//            } else if (stationaryPosition == 2) {
//                // Lying down on stomach
//                lyingStomachClassifier(inputFeatures)
//            } else if (stationaryPosition == 3) {
//                // Lying down on right
//                lyingRightClassifier(inputFeatures)
//            } else {
//                // Lying down on left
//                lyingLeftClassifier(inputFeatures)
//            }

        } catch (e: Exception) {
            Log.e("STATIONARY OR MOVING CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    private fun stationaryOrMovingClassifier(inputFeatures: TensorBuffer): Int {
        try {
            val model = StationaryOrMoving.newInstance(this)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            model.close()

            // 0 stationary, 1 moving
            return maxPosition

        } catch (e: Exception) {
            Log.e("STATIONARY OR MOVING CLASSIFIER", "An error occurred: ${e.message}")
        }
        return -1
    }

    private fun movingClassifier(inputFeatures: TensorBuffer) {
        try {
            val model = MovingClassifier.newInstance(this)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            val classes = arrayOf(
                "walking",
                "ascending_stairs",
                "descending_stairs",
                "shuffle_walking",
                "running",
                "misc_movements"
            )

            val result: TextView = findViewById(R.id.classificationText)
            result.setText(classes[maxPosition])

            model.close()

        } catch (e: Exception) {
            Log.e("MOVING CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    private fun stationaryPositionClassifier(inputFeatures: TensorBuffer): Int {
        try {
            val model = StationaryPositionClassifier.newInstance(this)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            model.close()

            return maxPosition

        } catch (e: Exception) {
            Log.e("STATIONARY POSITION CLASSIFIER", "An error occurred: ${e.message}")
        }
        return -1
    }

    private fun sittingOrStandingClassifier(inputFeatures: TensorBuffer) {
        try {
            // Runs model inference and gets result.
            val model = SittingStandingModel.newInstance(this)
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            val classes = arrayOf(
                "sitting_or_standing&coughing",
                "sitting_or_standing&hyperventilating",
                "sitting_or_standing&normal_breathing",
                "sitting_or_standing&talking",
                "sitting_or_standing&eating",
                "sitting_or_standing&singing",
                "sitting_or_standing&laughing"
            )

            val result: TextView = findViewById(R.id.classificationText)
            val displayText = "sittingOrStanding&" + classes[maxPosition]
            result.setText(displayText)
            model.close()
        } catch (e: Exception) {
            Log.e("LYING BACK CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    private fun lyingBackClassifier(inputFeatures: TensorBuffer) {
        try {
            // Runs model inference and gets result.
            val model = LyingDownBackModel.newInstance(this)
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            val classes = arrayOf(
                "coughing",
                "hyperventilating",
                "talking",
                "singing",
                "laughing",
                "normal_breathing"
            )

            val result: TextView = findViewById(R.id.classificationText)
            val displayText = "lyingBack&" + classes[maxPosition]
            result.setText(displayText)
            model.close()
        } catch (e: Exception) {
            Log.e("LYING BACK CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    private fun lyingStomachClassifier(inputFeatures: TensorBuffer) {
        try {
            // Runs model inference and gets result.
            val model = LyingDownStomachModel.newInstance(this)
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            val classes = arrayOf(
                "coughing",
                "hyperventilating",
                "talking",
                "singing",
                "laughing",
                "normal_breathing"
            )

            val result: TextView = findViewById(R.id.classificationText)
            val displayText = "lyingStomach&" + classes[maxPosition]
            result.setText(displayText)

            model.close()
        } catch (e: Exception) {
            Log.e("LYING STOMACH CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    private fun lyingRightClassifier(inputFeatures: TensorBuffer) {
        try {
            // Runs model inference and gets result.
            val model = LyingDownRightModel.newInstance(this)
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            val classes = arrayOf(
                "coughing",
                "hyperventilating",
                "talking",
                "singing",
                "laughing",
                "normal_breathing"
            )

            val result: TextView = findViewById(R.id.classificationText)
            val displayText = "lyingRight&" + classes[maxPosition]
            result.setText(displayText)

            model.close()
        } catch (e: Exception) {
            Log.e("LYING RIGHT CLASSIFIER", "An error occurred: ${e.message}")
        }
    }

    private fun lyingLeftClassifier(inputFeatures: TensorBuffer) {
        try {
            // Runs model inference and gets result.
            val model = LyingDownLeftModel.newInstance(this)
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            val classes = arrayOf(
                "coughing",
                "hyperventilating",
                "talking",
                "singing",
                "laughing",
                "normal_breathing"
            )

            val result: TextView = findViewById(R.id.classificationText)
            val displayText = "lyingLeft&" + classes[maxPosition]
            result.setText(displayText)

            model.close()
        } catch (e: Exception) {
            Log.e("LYING LEFT CLASSIFIER", "An error occurred: ${e.message}")
        }
    }


    private fun getMaxPosition(confidences: FloatArray): Int {
        var maxPosition = 0
        var maxConfidence = 0F
        for (i in confidences.indices) {
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i]
                maxPosition = i
            }
        }
        return maxPosition
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        looperRespeck.quit()
    }
}
