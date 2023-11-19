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
import com.specknet.pdiotapp.ml.Cnn2
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date

class ClassifyActivity: AppCompatActivity() {

    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    lateinit var lastThreeSecondsData: ArrayList<Array<Float>>

    private lateinit var database: DatabaseReference

//    private lateinit var testButton : Button

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


        val result: TextView = findViewById(R.id.classify_box_text)

//        testButton = findViewById(R.id.testButton)


//        result.setText("Is this working")

        setupDataList()

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("Classify activity", "Running on thread: " + Thread.currentThread().name)

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

//        testButton.setOnClickListener {
//
//            //Get User UID of current user logged in
//            val user = FirebaseAuth.getInstance().currentUser?.uid.toString()
//
//            val currentTime = System.currentTimeMillis()
//
//            val formattedTimeAndDate = SimpleDateFormat("HH:mm:ss")
//
//            val extractedDateFormatter = SimpleDateFormat("MMM dd,yyyy")
//
//
//
//            //val testDate: Date = Date(currentTime)
//
//            val testDate = "Nov 23,2023"
//            val currentDateAndTime = formattedTimeAndDate.format(currentTime)
//            val currentDateOnly = extractedDateFormatter.format(currentTime)
//
//            database.child(user).child(currentDateOnly).child(currentDateAndTime).setValue("test")
//
//
////            if (currentDateOnly == testDate){
////                database.child(user).child(currentDateAndTime).setValue("test")
////            }else{
////                database.child(user).child(currentDateAndTime).setValue("test")
////            }
//
//            //Get the current time
//            //val currentTime = System.currentTimeMillis()
//
//            //Get the current activity
//            //val currentActivity = result.text.toString()
//
//            //Create a new entry in the database
//
//            //database.child("users").child(user.toString()).child("activity").child(currentTime.toString()).setValue(currentActivity)
//
//
//        }




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
            val model = Cnn2.newInstance(context)

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

//            runOnUiThread {
//                val result: TextView = findViewById(R.id.displayText)
//                result.setText("working")   //CLASSES[maxPosition]
//            }
            val result: TextView = findViewById(R.id.classify_box_text)
            result.setText(CLASSES[maxPosition])   //CLASSES[maxPosition]

            database = FirebaseDatabase.getInstance("https://pdiotapp-5c2f8-default-rtdb.europe-west1.firebasedatabase.app/")
                .reference
            val user = FirebaseAuth.getInstance().currentUser?.uid.toString()

            val currentTime = System.currentTimeMillis()

            val formattedTimeAndDate = SimpleDateFormat("HH:mm:ss")

            val extractedDateFormatter = SimpleDateFormat("MMM dd,yyyy")

            val currentDateAndTime = formattedTimeAndDate.format(currentTime)
            val currentDateOnly = extractedDateFormatter.format(currentTime)
            database.child(user).child(currentDateOnly).child(currentDateAndTime).setValue(currentDateAndTime + " : " + CLASSES[maxPosition])





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