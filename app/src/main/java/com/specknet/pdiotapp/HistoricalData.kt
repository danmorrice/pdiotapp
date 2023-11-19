package com.specknet.pdiotapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.specknet.pdiotapp.live.LiveDataActivity
import kotlinx.android.synthetic.main.activity_historical_data.fetchHistoricalDataButton

class HistoricalData : AppCompatActivity() {

    private lateinit var database : DatabaseReference

    private lateinit var dropDownMenu : Spinner

    private lateinit var dataTextBoxOfRetrievedData : TextView

    private lateinit var returnToLiveDataButton : Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historical_data)

        val user = FirebaseAuth.getInstance().currentUser?.uid.toString()

        database = FirebaseDatabase.getInstance("https://pdiotapp-5c2f8-default-rtdb.europe-west1.firebasedatabase.app/")
            .reference.child(user)

        dropDownMenu = findViewById(R.id.historicalDropDownMenu)
        dataTextBoxOfRetrievedData = findViewById(R.id.DataTextBoxOfRetrievedData)
        returnToLiveDataButton = findViewById(R.id.returnToLiveDataButton)

        //Have the spinner be a list of nodes from the database

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dateList = ArrayList<String>()

                for (i in snapshot.children) {
                    dateList.add(i.key.toString())
                }

                val adapter = ArrayAdapter(this@HistoricalData, android.R.layout.simple_spinner_item, dateList)

                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

                dropDownMenu.adapter = adapter
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HistoricalData, "Error connecting to the database", Toast.LENGTH_SHORT).show()
            }
        })

        fetchHistoricalDataButton.setOnClickListener {
            val selectedDate = dropDownMenu.selectedItem.toString()
            fetchDataFromDatabaseForDate(selectedDate)
        }

        returnToLiveDataButton.setOnClickListener {
            val intent = Intent(this, LiveDataActivity::class.java)
            startActivity(intent)
        }


    }

    private fun fetchDataFromDatabaseForDate(selectedDate : String) {
        val user = FirebaseAuth.getInstance().currentUser?.uid.toString()

        database = FirebaseDatabase.getInstance("https://pdiotapp-5c2f8-default-rtdb.europe-west1.firebasedatabase.app/")
            .reference.child(user)

        database.child(selectedDate).addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                val dataStringBuilder = StringBuilder()

                for (dataSnapshot in snapshot.children) {
                    val data = dataSnapshot.getValue().toString()
                    dataStringBuilder.append(data)
                    dataStringBuilder.append("\n")
                }

                dataTextBoxOfRetrievedData.text = dataStringBuilder.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HistoricalData, "Error connecting to the database", Toast.LENGTH_SHORT).show()
                Log.d("HistoricalData", "Error connecting to the database")
            }
        })


    }

    //Read the data from the database and display it in the scrollable text block with the id DataTextBoxOfRetrievedData


}

