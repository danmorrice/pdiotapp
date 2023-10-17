package com.specknet.pdiotapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import com.specknet.pdiotapp.bluetooth.BluetoothSpeckService
import com.specknet.pdiotapp.bluetooth.ConnectingActivity
import com.specknet.pdiotapp.classify.ClassifyActivity
import com.specknet.pdiotapp.live.LiveDataActivity
import com.specknet.pdiotapp.onboarding.OnBoardingActivity
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.Utils
import kotlinx.android.synthetic.main.activity_main.coordinatorLayout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // buttons and textviews
    lateinit var liveProcessingButton: Button
    lateinit var pairingButton: Button
//    lateinit var recordButton: Button
    lateinit var classifyButton: Button

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // check whether the onboarding screen should be shown
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
        if (sharedPreferences.contains(Constants.PREF_USER_FIRST_TIME)) {
            isUserFirstTime = false
        }
        else {
            isUserFirstTime = true
            sharedPreferences.edit().putBoolean(Constants.PREF_USER_FIRST_TIME, false).apply()
            val introIntent = Intent(this, OnBoardingActivity::class.java)
            startActivity(introIntent)
        }

        liveProcessingButton = findViewById(R.id.live_button)
        pairingButton = findViewById(R.id.ble_button)
        classifyButton = findViewById(R.id.classify_button)
//        recordButton = findViewById(R.id.record_button)

        permissionAlertDialog = AlertDialog.Builder(this)

        setupClickListeners()

        setupPermissions()

        setupBluetoothService()

        // register a broadcast receiver for respeck status
        filter.addAction(Constants.ACTION_RESPECK_CONNECTED)
        filter.addAction(Constants.ACTION_RESPECK_DISCONNECTED)

    }

    fun setupClickListeners() {
        liveProcessingButton.setOnClickListener {
            val intent = Intent(this, LiveDataActivity::class.java)
            startActivity(intent)
        }

        pairingButton.setOnClickListener {
            val intent = Intent(this, ConnectingActivity::class.java)
            startActivity(intent)
        }

        classifyButton.setOnClickListener {
            val intent = Intent(this, ClassifyActivity::class.java)
            startActivity(intent)
        }
    }

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

fun main() {
    val MEDIA_TYPE_MARKDOWN = "text/x-markdown; charset=utf-8".toMediaType()
    //Create a list using LYING_LEFT_SINGING
    val LYING_LEFT_SINGING = listOf(
        listOf("-0.6437988", "-0.014953613", "-0.77716064", "-0.140625", "0.578125", "0.265625"),
        listOf("-0.6464844", "-0.0071411133", "-0.7774048", "-0.609375", "-0.453125", "0.515625"),
        listOf("-0.638916", "-0.014465332", "-0.78082275", "-1.609375", "0.171875", "0.53125"),
        listOf("-0.63134766", "-0.026428223", "-0.7605591", "-0.265625", "0.453125", "0.1875"),
        listOf("-0.6166992", "-0.0154418945", "-0.7546997", "0.21875", "0.515625", "-0.046875"),
        listOf("-0.6113281", "-0.014953613", "-0.7197876", "0.015625", "1.125", "0.765625"),
        listOf("-0.6755371", "-0.026916504", "-0.82696533", "-1.765625", "-1.5625", "0.734375"),
        listOf("-0.66625977", "-0.026672363", "-0.8245239", "0.4375", "1.515625", "-0.1875"),
        listOf("-0.61865234", "-0.011779785", "-0.7542114", "0.34375", "1.015625", "0.0"),
        listOf("-0.59936523", "-0.015197754", "-0.74053955", "0.28125", "0.765625", "0.109375"),
        listOf("-0.61987305", "-0.019104004", "-0.75128174", "-0.796875", "1.03125", "0.40625"),
        listOf("-0.6345215", "-0.016662598", "-0.7649536", "-0.59375", "1.09375", "0.5"),
        listOf("-0.6333008", "-0.023742676", "-0.7564087", "0.078125", "0.96875", "0.265625"),
        listOf("-0.6286621", "-0.026428223", "-0.7625122", "0.671875", "0.484375", "0.1875"),
        listOf("-0.6352539", "-0.023742676", "-0.7827759", "1.59375", "0.15625", "-0.109375"),
        listOf("-0.63012695", "-0.010314941", "-0.7859497", "1.28125", "-0.953125", "0.015625"),
        listOf("-0.61572266", "-0.0134887695", "-0.77008057", "0.734375", "-0.4375", "-0.140625"),
        listOf("-0.62646484", "-0.016906738", "-0.788147", "-0.984375", "-0.515625", "0.203125"),
        listOf("-0.6347656", "-0.014221191", "-0.79229736", "-1.796875", "-0.734375", "0.234375"),
        listOf("-0.6254883", "-0.022521973", "-0.7732544", "-1.3125", "0.328125", "-0.140625"),
        listOf("-0.6230469", "-0.024963379", "-0.78326416", "-0.8125", "-0.71875", "-0.3125"),
        listOf("-0.6218262", "-0.019836426", "-0.7942505", "-1.078125", "0.078125", "0.0"),
        listOf("-0.61450195", "-0.028381348", "-0.77008057", "-0.328125", "0.828125", "-0.09375"),
        listOf("-0.61376953", "-0.02130127", "-0.784729", "-0.28125", "1.609375", "-0.34375"),
        listOf("-0.6242676", "-0.023742676", "-0.79107666", "-0.03125", "1.171875", "-0.078125"),
        listOf("-0.6281738", "-0.01763916", "-0.7842407", "-0.28125", "1.96875", "-0.4375"),
        listOf("-0.6171875", "-0.022277832", "-0.7810669", "-0.3125", "2.265625", "-0.203125"),
        listOf("-0.6159668", "-0.018127441", "-0.7593384", "-0.421875", "1.453125", "-0.15625"),
        listOf("-0.6164551", "-0.01739502", "-0.767395", "-0.796875", "1.234375", "-0.109375"),
        listOf("-0.6213379", "-0.022521973", "-0.7810669", "-0.078125", "1.8125", "0.0"),
        listOf("-0.61621094", "-0.016662598", "-0.77008057", "-0.65625", "1.140625", "-0.0625"),
        listOf("-0.6291504", "-0.025939941", "-0.7871704", "-1.671875", "0.125", "0.015625"),
        listOf("-0.62060547", "-0.024230957", "-0.770813", "-1.40625", "0.34375", "0.03125"),
        listOf("-0.6176758", "-0.02154541", "-0.77178955", "-0.375", "-0.390625", "-0.1875"),
        listOf("-0.61938477", "-0.02545166", "-0.7896118", "-0.609375", "-1.328125", "0.046875"),
        listOf("-0.6135254", "-0.027160645", "-0.7781372", "-0.3125", "-0.765625", "-0.03125"),
        listOf("-0.62646484", "-0.023986816", "-0.8064575", "-0.640625", "-0.28125", "-0.109375"),
        listOf("-0.61621094", "-0.0335083", "-0.7896118", "-0.21875", "0.765625", "-0.53125"),
        listOf("-0.6086426", "-0.023498535", "-0.7962036", "-0.546875", "0.21875", "-0.359375"),
        listOf("-0.6062012", "-0.023498535", "-0.7815552", "-0.296875", "0.4375", "0.09375"),
        listOf("-0.611084", "-0.022033691", "-0.7752075", "0.109375", "1.28125", "0.046875"),
        listOf("-0.6052246", "-0.031555176", "-0.7839966", "-0.234375", "0.890625", "0.359375"),
        listOf("-0.6081543", "-0.026672363", "-0.7774048", "-0.3125", "1.015625", "0.3125"),
        listOf("-0.62109375", "-0.02545166", "-0.7888794", "-0.296875", "0.46875", "0.390625"),
        listOf("-0.6135254", "-0.020812988", "-0.8008423", "0.328125", "0.703125", "0.03125"),
        listOf("-0.6101074", "-0.023254395", "-0.7937622", "0.0", "0.34375", "0.40625"),
        listOf("-0.60253906", "-0.023498535", "-0.7737427", "0.78125", "-0.015625", "0.109375"),
        listOf("-0.6052246", "-0.019348145", "-0.7752075", "-0.390625", "0.671875", "0.25"),
        listOf("-0.609375", "-0.009094238", "-0.7905884", "-0.046875", "1.375", "0.359375"),
        listOf("-0.61987305", "-0.024719238", "-0.7966919", "-1.234375", "1.828125", "0.3125"),
        listOf("-0.61376953", "-0.024719238", "-0.7866821", "-0.171875", "1.625", "0.265625"),
        listOf("-0.62109375", "-0.02935791", "-0.78326416", "-0.59375", "0.96875", "0.125"),
        listOf("-0.6088867", "-0.02178955", "-0.79693604", "0.09375", "0.734375", "0.171875"),
        listOf("-0.607666", "-0.025939941", "-0.7769165", "-0.328125", "1.09375", "0.421875"),
        listOf("-0.61083984", "-0.016174316", "-0.78692627", "0.0625", "0.703125", "0.375"),
        listOf("-0.6142578", "-0.02130127", "-0.7937622", "0.015625", "0.734375", "0.3125"),
        listOf("-0.61206055", "-0.02545166", "-0.7920532", "0.34375", "0.765625", "0.171875"),
        listOf("-0.60668945", "-0.020812988", "-0.786438", "-0.25", "1.015625", "0.1875"),
        listOf("-0.6098633", "-0.027404785", "-0.784729", "-0.609375", "1.125", "0.265625"),
        listOf("-0.6113281", "-0.024719238", "-0.78985596", "-0.453125", "0.90625", "0.09375"),
        listOf("-0.61621094", "-0.027648926", "-0.79644775", "-1.21875", "1.265625", "0.40625"),
        listOf("-0.6027832", "-0.0154418945", "-0.79229736", "0.21875", "0.796875", "0.15625"),
        listOf("-0.6140137", "-0.026428223", "-0.77716064", "-0.546875", "1.203125", "0.203125"),
        listOf("-0.6142578", "-0.030822754", "-0.79107666", "0.0625", "1.046875", "0.28125"),
        listOf("-0.6154785", "-0.02935791", "-0.7918091", "-0.265625", "0.859375", "0.265625"),
        listOf("-0.6184082", "-0.024230957", "-0.78570557", "0.078125", "0.6875", "0.265625"),
        listOf("-0.60791016", "-0.023498535", "-0.7925415", "0.078125", "0.21875", "0.359375"),
        listOf("-0.60302734", "-0.018615723", "-0.7866821", "0.140625", "0.65625", "0.28125"),
        listOf("-0.6047363", "-0.024475098", "-0.7854614", "-0.375", "0.96875", "0.34375"),
        listOf("-0.611084", "-0.018371582", "-0.78570557", "-0.03125", "1.375", "0.34375"),
        listOf("-0.6152344", "-0.022277832", "-0.7876587", "-0.234375", "1.40625", "0.21875"),
        listOf("-0.61816406", "-0.027404785", "-0.7937622", "-0.21875", "1.28125", "0.125"),
        listOf("-0.61499023", "-0.022766113", "-0.79400635", "-0.140625", "1.140625", "0.125"),
        listOf("-0.61621094", "-0.027893066", "-0.78131104", "-0.015625", "1.03125", "0.21875"),
        listOf("-0.6057129", "-0.032043457", "-0.767395", "0.640625", "1.0625", "0.078125")
    )
    //var LYING_LEFT_SINGING = List(75) { arrayOf("-0.6437988", "-0.014953613", "-0.77716064", "-0.140625", "0.578125", "0.265625") }
    var test = LYING_LEFT_SINGING.toString().toRequestBody(MEDIA_TYPE_MARKDOWN)
    val request = Request.Builder()
        .url("http://iestynmullinor.pythonanywhere.com/predict")
        .post(LYING_LEFT_SINGING.toString().toRequestBody(MEDIA_TYPE_MARKDOWN))
        .build()
    val client = okhttp3.OkHttpClient()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        println(response.body!!.string())
    }

}