package com.specknet.pdiotapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.specknet.pdiotapp.bluetooth.BluetoothSpeckService
import com.specknet.pdiotapp.bluetooth.ConnectingActivity
import com.specknet.pdiotapp.live.LiveDataActivity
import com.specknet.pdiotapp.onboarding.OnBoardingActivity
import com.specknet.pdiotapp.classify.ClassifyActivity
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.Utils
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // buttons and textviews
//    lateinit var liveProcessingButton: Button
//    lateinit var pairingButton: Button
//    lateinit var recordButton: Button
//    lateinit var classifyButton: Button

    // permissions
    lateinit var permissionAlertDialog: AlertDialog.Builder

    val permissionsForRequest = arrayListOf<String>()

    var locationPermissionGranted = false
    var cameraPermissionGranted = false
    var readStoragePermissionGranted = false
    var writeStoragePermissionGranted = false

    // broadcast receiver
    val filter = IntentFilter()

    var isUserFirstTime = false

    private lateinit var database : DatabaseReference

    private lateinit var helpButton : Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // check whether the onboarding screen should be shown
        val sharedPreferences =
            getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
        if (sharedPreferences.contains(Constants.PREF_USER_FIRST_TIME)) {
            isUserFirstTime = false
        } else {
            isUserFirstTime = true
            sharedPreferences.edit().putBoolean(Constants.PREF_USER_FIRST_TIME, false).apply()
            val introIntent = Intent(this, OnBoardingActivity::class.java)
            startActivity(introIntent)
        }

        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigator)
        bottomNavigationView.selectedItemId = R.id.home_page
        //bottomNavigationView.menu.findItem(R.id.home_page).isChecked = true

        helpButton = findViewById(R.id.help_button)


//        liveProcessingButton = findViewById(R.id.live_button)
//        pairingButton = findViewById(R.id.ble_button)
//        classifyButton = findViewById(R.id.classify_button)
//        recordButton = findViewById(R.id.record_button)

        permissionAlertDialog = AlertDialog.Builder(this)

//        setupClickListeners()

        setupPermissions()

        setupBluetoothService()

        // register a broadcast receiver for respeck status
        filter.addAction(Constants.ACTION_RESPECK_CONNECTED)
        filter.addAction(Constants.ACTION_RESPECK_DISCONNECTED)

        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home_page -> {
                    //val intent = Intent(this, MainActivity::class.java)
                    //startActivity(intent)
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

        val greetingText = findViewById<TextView>(R.id.welcome_message)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour > 4 && currentHour < 12) {
            greetingText.text = "Good morning!"
        }else if (currentHour > 12 && currentHour < 18){
            greetingText.text = "Good afternoon!"
        }
        else if ((currentHour > 18 && currentHour < 24) || (currentHour < 4 && currentHour >= 0)) {
            greetingText.text = "Good evening!"
        }

        val user = FirebaseAuth.getInstance().currentUser?.uid.toString()

        database = FirebaseDatabase.getInstance("https://pdiotapp-5c2f8-default-rtdb.europe-west1.firebasedatabase.app/")
            .reference.child(user)

        //Get the most recent data entry from the database based on timestamp
        database.orderByKey().limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var lastGrandChildKey = ""
                    var lastGrandChildValue = ""
                    if (snapshot.exists()){
                        val orderedSnapshot = snapshot.children.sortedBy { it.key }.toList()
                        //Get the snapshot name
                        val i = 0

                        for (dataSnapshot in orderedSnapshot) {

                            for (grandChild in dataSnapshot.children){
                                val grandChildKey = grandChild.key.toString()
                                val grandChildValue = grandChild.value.toString()

                                lastGrandChildKey = grandChildKey
                                lastGrandChildValue = grandChildValue
                            }
                        }
                        Log.d("debug grandchild", lastGrandChildKey + " " + lastGrandChildValue)

                        val partsOfLastGrandChildValue = lastGrandChildValue.split(" :")
                        val lastGrandChildValueWithoutTimestamp = partsOfLastGrandChildValue[1]

                        val dataTextBoxOfRetrievedData = findViewById<TextView>(R.id.recent_classify_box_text)
                        dataTextBoxOfRetrievedData.text = lastGrandChildValueWithoutTimestamp

                    }else{
                        val dataTextBoxOfRetrievedData = findViewById<TextView>(R.id.recent_classify_box_text)
                        dataTextBoxOfRetrievedData.text = "You have no saved Classifications"
                    }

                }

                override fun onCancelled(error: DatabaseError) {
                    val dataTextBoxOfRetrievedData = findViewById<TextView>(R.id.recent_classify_box_text)
                    dataTextBoxOfRetrievedData.text = "You have no saved Classifications"
                }
            })

        setupBarChart()

        helpButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("About Our App")
            builder.setMessage(
                "1. Connect to the Respeck in Settings \n" +
                        "2. View your live data readings measured by the Respeck and your previously classified data in the Data screen \n" +
                        "3. You can classify your current activity in the Classify screen using one of our models \n" +
                        "4. Each Classification is made based on an initial 5s of data and then 1s intervals after that \n"
            )
            builder.setPositiveButton("OK") { dialog, which ->
                Toast.makeText(applicationContext, "OK", Toast.LENGTH_SHORT)
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }

    }



//    fun setupClickListeners() {
//        liveProcessingButton.setOnClickListener {
//            val intent = Intent(this, LiveDataActivity::class.java)
//            startActivity(intent)
//        }
//
//        pairingButton.setOnClickListener {
//            val intent = Intent(this, ConnectingActivity::class.java)
//            startActivity(intent)
//        }
//
//        classifyButton.setOnClickListener {
//            val intent = Intent(this, ClassifyActivity::class.java)
//            startActivity(intent)
//        }
//    }

    fun setupPermissions() {
        // request permissions

        // location permission
        Log.i("Permissions", "Location permission = " + locationPermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsForRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsForRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        else {
            locationPermissionGranted = true
        }

        // camera permission
        Log.i("Permissions", "Camera permission = " + cameraPermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Camera permission = " + cameraPermissionGranted)
            permissionsForRequest.add(Manifest.permission.CAMERA)
        }
        else {
            cameraPermissionGranted = true
        }

        // read storage permission
        Log.i("Permissions", "Read st permission = " + readStoragePermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Read st permission = " + readStoragePermissionGranted)
            permissionsForRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        else {
            readStoragePermissionGranted = true
        }

        // write storage permission
        Log.i("Permissions", "Write storage permission = " + writeStoragePermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Write storage permission = " + writeStoragePermissionGranted)
            permissionsForRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        else {
            writeStoragePermissionGranted = true
        }

        if (permissionsForRequest.size >= 1) {
            ActivityCompat.requestPermissions(this,
                permissionsForRequest.toTypedArray(),
                Constants.REQUEST_CODE_PERMISSIONS)
        }

    }

    fun setupBluetoothService() {
        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, applicationContext)
        Log.i("debug","isServiceRunning = " + isServiceRunning)

        // check sharedPreferences for an existing Respeck id
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
        if (sharedPreferences.contains(Constants.RESPECK_MAC_ADDRESS_PREF)) {
            Log.i("sharedpref", "Already saw a respeckID, starting service and attempting to reconnect")

            // launch service to reconnect
            // start the bluetooth service if it's not already running
            if(!isServiceRunning) {
                Log.i("service", "Starting BLT service")
                val simpleIntent = Intent(this, BluetoothSpeckService::class.java)
                this.startService(simpleIntent)
            }
        }
        else {
            Log.i("sharedpref", "No Respeck seen before, must pair first")
            // TODO then start the service from the connection activity
        }
    }

    fun setupBarChart() {
        val user = FirebaseAuth.getInstance().currentUser?.uid.toString()

        database = FirebaseDatabase.getInstance("https://pdiotapp-5c2f8-default-rtdb.europe-west1.firebasedatabase.app/")
            .reference.child(user)

        val endDate = Date()
        val calendar = Calendar.getInstance()
        calendar.time = endDate

        val dataFormat = SimpleDateFormat("MMM dd,yyyy")

        val entries = HashMap<Int, Float>()
        val dates = HashMap<Int, String>()
//        val entries = ArrayList<BarEntry>()
//        val dates = ArrayList<String>()
        var queriesCompleted = 0

        for (i in 0 until 7) { // Iterate over the past 7 days
            val currentDate = calendar.time
            val currentDateString = dataFormat.format(currentDate)
            Log.d("debug", "currentDateString = $currentDateString")
            var entryCountForChildNode = 0f

            database.child(currentDateString)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.d("debug", "snapshot = $snapshot")
                        if (snapshot.exists()){
                            entryCountForChildNode = snapshot.childrenCount.toFloat()
                            Log.d("debug", entryCountForChildNode.toString())
                        } else {
                            // Date not found in the database, set count to 0
                            0f
                            Log.d("debug", "snapshot.childrenCount = 0")
                        }
                        entries[i] = entryCountForChildNode
                        dates[i] = currentDateString
//                        entries.add(BarEntry(i.toFloat(), entryCountForChildNode))
//                        dates.add(currentDateString)

                        queriesCompleted++
                        if (queriesCompleted == 7) {
//                            setupChart(entries, dates)
                            val sortedEntries = entries.toSortedMap()
                            val sortedDates = dates.toSortedMap()
                            val sortedEntriesArrayList = ArrayList<BarEntry>()
                            val sortedDatesArrayList = ArrayList<String>()
                            for (entry in sortedEntries) {
                                sortedEntriesArrayList.add(BarEntry(entry.key.toFloat(), entry.value))
                            }
                            for (date in sortedDates) {
                                sortedDatesArrayList.add(date.value)
                            }
                            setupChart(sortedEntriesArrayList, sortedDatesArrayList)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(applicationContext, "Error retrieving data", Toast.LENGTH_SHORT).show()
                    }
                })

            calendar.add(Calendar.DAY_OF_MONTH, -1) // Move to the previous day
        }
    }

    private fun setupChart(entries: ArrayList<BarEntry>, dates : List<String>) {
        val barDataSet = BarDataSet(entries, "Number of Classifications Made on Each Day")
        val barData = BarData(barDataSet)

        val barChart = findViewById<com.github.mikephil.charting.charts.BarChart>(R.id.bar_chart)

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(dates)
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setCenterAxisLabels(false)
        xAxis.setDrawGridLines(false)
        xAxis.textSize = 8f

        barChart.data = barData
        barChart.setFitBars(true)
        barChart.invalidate()
        barChart.description.isEnabled = false
        barChart.setExtraOffsets(5f, 60f, 5f, 20f)

        val legend = barChart.legend
        legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER

    }

    class DateAxisValueFormatter(private val dates: List<String>) : ValueFormatter() {
        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            val index = value.toInt()
            return if (index >= 0 && index < dates.size) {
                dates[index]
            } else {
                ""
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        System.exit(0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if(grantResults.isNotEmpty()) {
                for (i in grantResults.indices) {
                    when(permissionsForRequest[i]) {
                        Manifest.permission.ACCESS_COARSE_LOCATION -> locationPermissionGranted = true
                        Manifest.permission.ACCESS_FINE_LOCATION -> locationPermissionGranted = true
                        Manifest.permission.CAMERA -> cameraPermissionGranted = true
                        Manifest.permission.READ_EXTERNAL_STORAGE -> readStoragePermissionGranted = true
                        Manifest.permission.WRITE_EXTERNAL_STORAGE -> writeStoragePermissionGranted = true
                    }

                }
            }
        }

        // count how many permissions need granting
        var numberOfPermissionsUngranted = 0
        if (!locationPermissionGranted) numberOfPermissionsUngranted++
        if (!cameraPermissionGranted) numberOfPermissionsUngranted++
        if (!readStoragePermissionGranted) numberOfPermissionsUngranted++
        if (!writeStoragePermissionGranted) numberOfPermissionsUngranted++

        // show a general message if we need multiple permissions
        if (numberOfPermissionsUngranted > 1) {
            val generalSnackbar = Snackbar
                .make(coordinatorLayout, "Several permissions are needed for correct app functioning", Snackbar.LENGTH_LONG)
                .setAction("SETTINGS") {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
                .show()
        }
        else if(numberOfPermissionsUngranted == 1) {
            var snackbar: Snackbar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_LONG)
            if (!locationPermissionGranted) {
                snackbar = Snackbar
                    .make(
                        coordinatorLayout,
                        "Location permission needed for Bluetooth to work.",
                        Snackbar.LENGTH_LONG
                    )
            }

            if(!cameraPermissionGranted) {
                snackbar = Snackbar
                    .make(
                        coordinatorLayout,
                        "Camera permission needed for QR code scanning to work.",
                        Snackbar.LENGTH_LONG
                    )
            }

            if(!readStoragePermissionGranted || !writeStoragePermissionGranted) {
                snackbar = Snackbar
                    .make(
                        coordinatorLayout,
                        "Storage permission needed to record sensor.",
                        Snackbar.LENGTH_LONG
                    )
            }

            snackbar.setAction("SETTINGS") {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                startActivity(settingsIntent)
            }
                .show()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.show_tutorial) {
            val introIntent = Intent(this, OnBoardingActivity::class.java)
            startActivity(introIntent)
            return true
        }

        return super.onOptionsItemSelected(item)
    }



}