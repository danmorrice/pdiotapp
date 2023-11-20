package com.specknet.pdiotapp.classify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
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

import com.specknet.pdiotapp.ml.Task1


class ClassifyActivity: AppCompatActivity() {

    private lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    private lateinit var looperRespeck: Looper
    private val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    private lateinit var nonGyroDataStream: ArrayList<Array<Float>>
    private lateinit var gyroDataStream: ArrayList<Array<Float>>

    private var nonGyroDataPointCounter = 0
    private var gyroDataPointCounter = 0

    private var noOfSecondsForModel = 4

    private lateinit var database: DatabaseReference


    private lateinit var task_one_model_button : Button
    private lateinit var task_two_model_button : Button
    private lateinit var task_three_model_button : Button
    private lateinit var task_four_model_button : Button




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classify)

        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigator)
        bottomNavigationView.selectedItemId = R.id.classify_page

        task_one_model_button = findViewById(R.id.task_1_model_button)
        task_two_model_button = findViewById(R.id.task_2_model_button)
        task_three_model_button = findViewById(R.id.task_3_model_button)
        task_four_model_button = findViewById(R.id.task_4_model_button)

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
                    //val intent = Intent(this, ClassifyActivity::class.java)
                    //startActivity(intent)
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

        task_one_model_button.setOnClickListener {
            if (!task_one_model_button.isSelected) {
                task_one_model_button.isSelected = true
                task_two_model_button.isSelected = false
                task_three_model_button.isSelected = false
                task_four_model_button.isSelected = false
                //TODO: ADD MODEL CODE HERE
                Toast.makeText(this, "Task 1 Model Selected", Toast.LENGTH_SHORT).show()
            }
        }

        task_two_model_button.setOnClickListener {
            if (!task_two_model_button.isSelected) {
                task_one_model_button.isSelected = false
                task_two_model_button.isSelected = true
                task_three_model_button.isSelected = false
                task_four_model_button.isSelected = false
                //TODO: ADD MODEL CODE HERE
                Toast.makeText(this, "Task 2 Model Selected", Toast.LENGTH_SHORT).show()
            }
        }

        task_three_model_button.setOnClickListener {
            if (!task_three_model_button.isSelected) {
                task_one_model_button.isSelected = false
                task_two_model_button.isSelected = false
                task_three_model_button.isSelected = true
                task_four_model_button.isSelected = false
                //TODO: ADD MODEL CODE HERE
                Toast.makeText(this, "Task 3 Model Selected", Toast.LENGTH_SHORT).show()
            }
        }

        task_four_model_button.setOnClickListener {
            if (!task_four_model_button.isSelected) {
                task_one_model_button.isSelected = false
                task_two_model_button.isSelected = false
                task_three_model_button.isSelected = false
                task_four_model_button.isSelected = true
                //TODO: ADD MODEL CODE HERE
                Toast.makeText(this, "Task 4 Model Selected", Toast.LENGTH_SHORT).show()
            }
        }

        //Get the database instance
        database = FirebaseDatabase.getInstance("https://pdiotapp-5c2f8-default-rtdb.europe-west1.firebasedatabase.app/")
            .reference

        setupDataStreams()
        streamDataToModel(false)

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
                        updateData(dataPoint, false)
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

        // If the ArrayList is full, remove the first element
        if (dataStream.size == 25*noOfSecondsForModel) {
            dataStream.removeAt(0)
        }
        // Add the new data point to the end of the ArrayList
        dataStream.add(dataPoint)
        nonGyroDataPointCounter++

        if (nonGyroDataPointCounter == 25) {
            nonGyroDataPointCounter = 0
            inputFeatures = prepareDataForClassification(dataStream, 3)
            classifyNonGyroActivity(inputFeatures)
        }


        // Classify activity after every 12 data points are read in (~0.5 seconds)
//        if (isGyroOn) {
//            gyroDataPointCounter++
//            if (gyroDataPointCounter == 12) {
//                gyroDataPointCounter = 0
//                inputFeatures = prepareDataForClassification(dataStream, 6)
//                // TODO: Use input features for gyroscope data
//            }
//        } else {
//            nonGyroDataPointCounter++
//            if (nonGyroDataPointCounter == 12) {
//                nonGyroDataPointCounter = 0
//                inputFeatures = prepareDataForClassification(dataStream, 3)
//                classifyNonGyroActivity(inputFeatures)
//            }
//        }
    }

    private fun prepareDataForClassification(data: ArrayList<Array<Float>>, dataWidth: Int): TensorBuffer {
        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 100, 3), DataType.FLOAT32)
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*100*3)
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
            if (task_one_model_button.isSelected) {
                taskOneClassifier(inputFeatures)
            }
            else {
                val result: TextView = findViewById(R.id.classify_box_text)
                result.setText("No model selected")
            }

        } catch (e: Exception) {
            Log.e("STATIONARY OR MOVING CLASSIFIER", "An error occurred: ${e.message}")
        }
    }


    private fun taskOneClassifier(inputFeatures: TensorBuffer) {
        try {
            val model = Task1.newInstance(this)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeatures)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray

            val maxPosition = getMaxPosition(confidences)

            val classes = arrayOf(
                "Sitting or standing",
                "Lying down on your back",
                "Lying down on your stomach",
                "Lying down on your right",
                "Lying down on your left",
                "Walking",
                "Ascending stairs",
                "Descending stairs",
                "Shuffle walking",
                "Running",
                "Miscellaneous"
            )

            val result: TextView = findViewById(R.id.classify_box_text)
            result.setText(classes[maxPosition])

            model.close()

        } catch (e: Exception) {
            Log.e("MOVING CLASSIFIER", "An error occurred: ${e.message}")
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

//val result: TextView = findViewById(R.id.classify_box_text)
//result.setText(CLASSES[maxPosition])   //CLASSES[maxPosition]
//
//database = FirebaseDatabase.getInstance("https://pdiotapp-5c2f8-default-rtdb.europe-west1.firebasedatabase.app/")
//.reference
//val user = FirebaseAuth.getInstance().currentUser?.uid.toString()
//
//val currentTime = System.currentTimeMillis()
//
//val formattedTimeAndDate = SimpleDateFormat("HH:mm:ss")
//
//val extractedDateFormatter = SimpleDateFormat("MMM dd,yyyy")
//
//val currentDateAndTime = formattedTimeAndDate.format(currentTime)
//val currentDateOnly = extractedDateFormatter.format(currentTime)
//database.child(user).child(currentDateOnly).child(currentDateAndTime).setValue(currentDateAndTime + " : " + CLASSES[maxPosition])


