package com.example.a2subscriber

import android.graphics.Color
import android.location.Location
import android.net.ParseException
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Optional
import java.util.UUID
import kotlin.math.abs


class MapActivity : AppCompatActivity(), OnMapReadyCallback,OnMoreButtonClickListener {

    private var mapReady = false
    private lateinit var mMap: GoogleMap
    private lateinit var dbHelper: DatabaseHelper
    private val pointsMap = hashMapOf<Int, MutableList<CustomMarkerPoints>>()
    private val recentPointsMap = hashMapOf<Int, MutableList<CustomMarkerPoints>>()
    private val studentInfo = mutableListOf<StudentInfo>()
    private var client: Mqtt5BlockingClient? = null
    var studentListAdapter: StudentAdapter? = null
    var curId: Int = 0;



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()

        // Adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = DatabaseHelper(this, null)


        // Set up the map fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        client = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816033593.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        setupDateRangeListeners()
        //testpoints()
        setupStudentAdapter()
        connectAndSubscribe()

        populateStudentInfo()
        dbHelper.logAllData()
    }

    private fun connectAndSubscribe() {
        try {
            client?.connect()
            client?.toAsync()?.subscribeWith()
                ?.topicFilter("assignment/location")
                ?.callback { message ->
                    val receivedPayload = message.payload
                        .flatMap { Optional.ofNullable(it) }
                        .map { byteBuffer ->
                            val byteArray = ByteArray(byteBuffer.remaining())
                            byteBuffer.get(byteArray)
                            String(byteArray)
                        }.orElse(null)
                    processPayload(receivedPayload)
                    runOnUiThread {
                        //textView.text = "Received: $receivedPayload"
                    }
                }
                ?.send()
        } catch (e: Exception) {
            Toast.makeText(this, "Error connecting or subscribing", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.disconnect()
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mapReady = true
        // Now that the map is ready, we can draw the initial data
        drawRecent()
    }


    private fun addMarkerAtLocationList(id: Int) {
        val pointList = dbHelper.getLocationDataById(id)
        Log.d("MapDrawing", "Retrieved ${pointList.size} points for ID: $id")
        pointsMap[id] = pointList
    }

    private fun addMarkerAtLocationRecent(id: Int){
        val pointList = dbHelper.getRecentLocationData(id)
        Log.d("MapDrawing", "Retrieved ${pointList.size} points for ID: $id")
        recentPointsMap[id] = pointList
    }

    private fun drawPolyline(id: Int) {
        // Extract LatLng points from the custom marker points
        val listOfPoints = pointsMap[id]
        val color = getColorForId(id)
        val latLngPoints = listOfPoints?.map { it.point }

        // Draw a polyline connecting all markers
        val polylineOptions = latLngPoints?.let {
            PolylineOptions()
                .addAll(it)
                .color(color)
                .width(5f)
                .geodesic(true)
        }

        latLngPoints?.firstOrNull()?.let { firstPoint ->
            mMap.addMarker(
                MarkerOptions()
                    .position(firstPoint)
                    .title("Last Position")
                    .snippet("ID: $id")
                    .icon(BitmapDescriptorFactory.defaultMarker(getHueFromColor(color)))
            )
        }

        if (polylineOptions != null) {
            mMap.addPolyline(polylineOptions)
        }

        // Adjust the camera view to fit all points
        val boundsBuilder = LatLngBounds.Builder()
        latLngPoints?.forEach { boundsBuilder.include(it) }
        val bounds = boundsBuilder.build()

        // Move camera to show all points with padding
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    private fun drawPolylineRecent(id: Int) {
        if (!mapReady) {
            Log.d("MapDrawing", "Map not ready yet")
            return
        }

        val listOfPoints = recentPointsMap[id]
        if (listOfPoints.isNullOrEmpty()) {
            Log.d("MapDrawing", "No points found for ID: $id")
            return
        }

        Log.d("MapDrawing", "Drawing ${listOfPoints.size} points for ID: $id")

        // Convert points to LatLng list
        val color = getColorForId(id)
        val latLngPoints = listOfPoints.map { it.point }

        latLngPoints.firstOrNull()?.let { firstPoint ->
            mMap.addMarker(
                MarkerOptions()
                    .position(firstPoint)
                    .title("Last Position")
                    .snippet("ID: $id")
                    .icon(BitmapDescriptorFactory.defaultMarker(getHueFromColor(color)))
            )
        }

        // Draw the polyline
        if (latLngPoints.size >= 2) {
            val polylineOptions = PolylineOptions()
                .addAll(latLngPoints)
                .color(color)
                .width(5f)
                .geodesic(true)

            mMap.addPolyline(polylineOptions)
        }
    }

    private fun drawId(id: Int){
        addMarkerAtLocationList(id)
        drawPolyline(id)
    }

    private fun drawRecent() {
        if (!mapReady) {
            Log.d("MapDrawing", "Map not ready in drawRecent")
            return
        }

        mMap.clear() // Clear existing markers and polylines

        val ids = dbHelper.getAllIds()
        Log.d("MapDrawing", "Found ${ids.size} IDs to draw")

        for (id in ids) {
            Log.d("MapDrawing", "Processing ID: $id")
            addMarkerAtLocationRecent(id)
            drawPolylineRecent(id)
        }

        fitCameraToPoints()
    }


    private fun processPayload(payload: String?) {
        payload?.let {
            try {
                val parts = payload.split(", ").map { it.split(": ").last() }
                if (parts.size == 4) {
                    val id = parts[0].toInt()
                    val latitude = parts[1].toDouble()
                    val longitude = parts[2].toDouble()
                    val timestamp = parts[3].toLong()

                    Log.d("ProcessPayload", "Attempting to insert: ID=$id, Lat=$latitude, Long=$longitude")

                    if (dbHelper.insertData(id, latitude, longitude, timestamp)) {
                        Log.d("ProcessPayload", "Data inserted successfully")
                        updateStudentSpeed(id)

                        // Update UI on main thread
                        runOnUiThread {
                            if (mapReady) {
                                drawRecent()
                                updateStudentSpeed(id)
                            }
                        }
                    } else {
                        Log.e("ProcessPayload", "Failed to insert data")
                    }
                } else {
                    Log.e("ProcessPayload", "Invalid payload format: $payload")
                }
            } catch (e: Exception) {
                Log.e("ProcessPayload", "Error processing payload", e)
            }
        }
    }

    private fun populateStudentInfo(id: Int, startTime: Long, endTime: Long) {
        // Retrieve location data for the given ID and time range
        val locationData = dbHelper.getDataInRange(id, startTime, endTime)
        Log.d("StudentInfo", "Location data for ID $id in range: ${locationData.size} points")

        if (locationData.size < 2) {
            runOnUiThread {
                findViewById<TextView>(R.id.maxspds).text = "Max Speed (KM/H): "+String.format("%.3f",0)
                findViewById<TextView>(R.id.minspds).text = "Min Speed (KM/H): "+String.format("%.3f", 0)
                findViewById<TextView>(R.id.avgspds).text = "Average Speed (KM/H): "+String.format("%.3f", 0)
            }
            Log.d("StudentInfo", "Skipping ID $id - not enough location points")
            return // Need at least two points to calculate speed
        }

        var maxSpeed = 0.0
        var minSpeed = Double.MAX_VALUE
        var totalSpeed = 0.0
        var count = 0

        // Iterate through the location data to calculate speed between consecutive points
        for (i in 1 until locationData.size) {
            val prevPoint = locationData[i - 1]
            val currentPoint = locationData[i]

            // Calculate distance between the points
            val distance = calculateDistance(prevPoint.point, currentPoint.point) / 1000 // Distance in km
            val timeDiff = abs(currentPoint.time - prevPoint.time) / 3600.0 // Time in hours

            val speed = distance / timeDiff
            Log.d("StudentInfo", "ID: $id, Current Point Speed: $speed, Distance: $distance, Time: $timeDiff")

            // Update max, min, and avg speed
            if (speed > maxSpeed) maxSpeed = speed
            if (speed < minSpeed) minSpeed = speed
            totalSpeed += speed
            count++
        }

        // Calculate average speed
        val avgSpeed = if (count > 0) totalSpeed / count else 0.0


        // Update the student list adapter
        runOnUiThread {
            findViewById<TextView>(R.id.maxspds).text = "Max Speed (KM/H): "+String.format("%.3f", maxSpeed)
            findViewById<TextView>(R.id.minspds).text = "Min Speed (KM/H): "+String.format("%.3f", minSpeed)
            findViewById<TextView>(R.id.avgspds).text = "Average Speed (KM/H): "+String.format("%.3f", avgSpeed)
        }
    }

    private fun populateStudentInfo() {
        val allIds = dbHelper.getAllIds()
        Log.d("StudentInfo", "Found ${allIds.size} student IDs")

        // For each ID, retrieve the location data and calculate speeds
        for (id in allIds) {
            val locationData = dbHelper.getLocationDataById(id)
            Log.d("StudentInfo", "Location data for ID $id: ${locationData.size} points")

            if (locationData.size < 2){
                Log.d("StudentInfo", "Skipping ID $id - not enough location points")
                continue // Need at least two points to calculate speed
            }


            var maxSpeed = 0.0
            var minSpeed = Double.MAX_VALUE
            var totalSpeed = 0.0
            var count = 0

            // Iterate through the location data to calculate speed between consecutive points
            for (i in 1 until locationData.size) {
                val prevPoint = locationData[i - 1]
                val currentPoint = locationData[i]

                // Calculate distance between the points
                val distance = calculateDistance(prevPoint.point, currentPoint.point)/1000 // Distance in Km
                val timeDiff = abs(currentPoint.time - prevPoint.time) / 60000.0 // Time in hours


                val speed = (distance) / (timeDiff)
                Log.d("Student Info", "ID: $id, current Point spd: $speed , dist: $distance, time: $timeDiff")

                // Update max, min, and avg speed
                if (speed > maxSpeed) maxSpeed = speed
                if (speed < minSpeed) minSpeed = speed
                totalSpeed += speed
                count++
            }

            // Calculate average speed
            val avgSpeed = if (count > 0) totalSpeed / count else 0.0

            // Create a StudentInfo object and add it to studentInfo list
            val student = StudentInfo(id, maxSpeed, minSpeed, avgSpeed, count)
            studentInfo.add(student)
            Log.d("StudentInfo", "Added student: ID=${student.id}id, MaxSpeed=${student.maxSpd}")
        }
        Log.d("StudentInfo", "Total students added: ${studentInfo.size}")

        studentListAdapter?.updateList(studentInfo)
        runOnUiThread {
            studentListAdapter?.updateList(studentInfo)
        }

    }

    private fun updateStudentSpeed(id: Int) {
        // Find the StudentInfo object for the given ID
        val student = studentInfo.find { it.id == id }

        if (student != null) {
            // Get the two most recent points for the given ID
            val recentPoints = dbHelper.getRecentLocationData(id).take(2)

            if (recentPoints.size == 2) {
                val recentPoint = recentPoints[0] // Most recent point
                val previousPoint = recentPoints[1] // Second most recent point

                // Calculate the speed between the two points
                val distance = calculateDistance(previousPoint.point, recentPoint.point)/1000 // Distance in meters
                val timeDiff = (recentPoint.time - previousPoint.time) / 60000.0 // Time difference in seconds

                if (timeDiff > 0) {
                    val speed = (distance) / (timeDiff) // Speed in km/h

                    // Update max, min, and average speeds
                    student.maxSpd = maxOf(student.maxSpd, speed)
                    student.minSpd = minOf(student.minSpd, speed)
                    student.avgSpd = ((student.avgSpd * student.totalEntries) + speed) / (student.totalEntries + 1)

                    // Increment the total entries counter
                    student.totalEntries++
                }
            }

            studentListAdapter?.updateList(studentInfo)
        }
    }

    // Function to calculate the distance between two LatLng points (in meters)
    private fun calculateDistance(from: LatLng, to: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0].toDouble() // Return distance in meters
    }

    override fun onMoreButtonClick(student: StudentInfo) {
        mMap.clear()
        drawId(student.id)
        curId = student.id

        var temp = dbHelper.getLocationDataById(student.id)
        findViewById<TextView>(R.id.maxspds).text = "Max Speed (KM/H): "+String.format("%.3f", student.maxSpd)
        findViewById<TextView>(R.id.minspds).text = "Min Speed (KM/H): "+String.format("%.3f", student.minSpd)
        findViewById<TextView>(R.id.avgspds).text = "Average Speed (KM/H): "+String.format("%.3f", student.avgSpd)

        findViewById<TextView>(R.id.startTitle).text = "Start Date"
        findViewById<TextView>(R.id.stopTitle).text = "Stop Date"
        findViewById<TextView>(R.id.startDate).text = "(oldest date: "+convertTimeToDate(temp.first().time)+")"
        findViewById<TextView>(R.id.EndDate).text = "(newest date: "+convertTimeToDate(temp.last().time)+")"
        findViewById<TextView>(R.id.maintitle).text = "Summary of "+student.id.toString()

        findViewById<TextView>(R.id.maxspds).visibility=View.VISIBLE
        findViewById<TextView>(R.id.minspds).visibility=View.VISIBLE
        findViewById<TextView>(R.id.avgspds).visibility=View.VISIBLE
        findViewById<TextView>(R.id.startDate).visibility = View.VISIBLE
        findViewById<TextView>(R.id.EndDate).visibility = View.VISIBLE
        findViewById<EditText>(R.id.startDateEditText).visibility = View.VISIBLE
        findViewById<EditText>(R.id.endDateEditText).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.moreInfo).visibility = View.VISIBLE
        findViewById<Button>(R.id.button).visibility = View.VISIBLE
        findViewById<TextView>(R.id.startTitle).visibility = View.VISIBLE
        findViewById<TextView>(R.id.stopTitle).visibility = View.VISIBLE
        findViewById<RecyclerView>(R.id.rvStudents).visibility = View.GONE
        findViewById<TextView>(R.id.liveView).visibility = View.GONE

    }

    fun backBtn(view: View) {
        mMap.clear()
        drawRecent()

        findViewById<TextView>(R.id.maintitle).text ="Assignment 2 - Subscriber"

        findViewById<TextView>(R.id.maxspds).visibility=View.GONE
        findViewById<TextView>(R.id.minspds).visibility=View.GONE
        findViewById<TextView>(R.id.avgspds).visibility=View.GONE
        findViewById<TextView>(R.id.startDate).visibility = View.GONE
        findViewById<TextView>(R.id.EndDate).visibility = View.GONE
        findViewById<Button>(R.id.button).visibility = View.GONE
        findViewById<EditText>(R.id.startDateEditText).visibility = View.GONE
        findViewById<EditText>(R.id.endDateEditText).visibility = View.GONE
        findViewById<TextView>(R.id.startTitle).visibility = View.GONE
        findViewById<TextView>(R.id.stopTitle).visibility = View.GONE
        findViewById<RecyclerView>(R.id.rvStudents).visibility = View.VISIBLE
        findViewById<TextView>(R.id.liveView).visibility = View.VISIBLE

    }

    private fun convertTimeToDate(seconds: Long): String {
        val date = Date(seconds * 1000) // Convert seconds to milliseconds
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) // Format as dd/MM/yyyy
        return format.format(date)
    }

    private fun dateToSeconds(dateString: String): Long {
        val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        formatter.isLenient = false // This prevents partial parsing

        return try {
            // Check if the input matches the expected format before parsing
            if (!dateString.matches("\\d{1,2}-\\d{1,2}-\\d{4}".toRegex())) {
                return -1L
            }

            val date = formatter.parse(dateString)
            date?.time?.div(1000) ?: -1L // Convert milliseconds to seconds
        } catch (e: ParseException) {
            -1L
        }
    }


    fun handleDateRangeUpdate(startSeconds: Long, endSeconds: Long) {
        val results: MutableList<CustomMarkerPoints>

        if (startSeconds > 0 && endSeconds > 0 && startSeconds <= endSeconds) {
            results = dbHelper.getDataInRange(curId, startSeconds, endSeconds)
            Log.d("Date", "Checking ranges for $curId")
            populateStudentInfo(curId,startSeconds, endSeconds)
            if(results.isNullOrEmpty()){
                mMap.clear()
            }else{
                pointsMap[curId]?.clear()
                pointsMap[curId]=results
                mMap.clear()
                drawPolyline(curId)
            }
        } else {
            Toast.makeText(this, "Invalid range", Toast.LENGTH_SHORT).show()
            mMap.clear()

        }
    }

    private fun setupDateRangeListeners() {
        val startDateField = findViewById<EditText>(R.id.startDateEditText)
        val endDateField = findViewById<EditText>(R.id.endDateEditText)

        val dateFormatRegex = "^\\d{2}-\\d{2}-\\d{4}$".toRegex()

        // TextWatcher for formatting input
        val autoFormatWatcher = { field: EditText ->
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val input = s.toString()
                    val cleanInput = input.replace("-", "")

                    // Add hyphens automatically
                    val formattedInput = when {
                        cleanInput.length > 2 && cleanInput.length <= 4 ->
                            "${cleanInput.substring(0, 2)}-${cleanInput.substring(2)}"

                        cleanInput.length > 4 ->
                            "${cleanInput.substring(0, 2)}-${
                                cleanInput.substring(
                                    2,
                                    4
                                )
                            }-${cleanInput.substring(4)}"

                        else -> cleanInput
                    }

                    // Prevent infinite loop
                    if (input != formattedInput) {
                        field.removeTextChangedListener(this)
                        field.setText(formattedInput)
                        field.setSelection(formattedInput.length)
                        field.addTextChangedListener(this)
                    }
                }
            }
        }


        // Attach format watchers
        startDateField.addTextChangedListener(autoFormatWatcher(startDateField))
        endDateField.addTextChangedListener(autoFormatWatcher(endDateField))

        // Shared TextWatcher for validation
        val validationWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val startDate = startDateField.text.toString().trim()
                val endDate = endDateField.text.toString().trim()

                // Log inputs to debug
                Log.d("Validation", "Start Date: $startDate, End Date: $endDate")

                if (startDate.matches(dateFormatRegex) && endDate.matches(dateFormatRegex)) {
                    val startSeconds = dateToSeconds(startDate)
                    val endSeconds = dateToSeconds(endDate)+86399

                    if (startSeconds != -1L && endSeconds != -1L) {
                        Log.d("Validation", "Dates are valid: Start Seconds=$startSeconds, End Seconds=$endSeconds")
                        handleDateRangeUpdate(startSeconds, endSeconds)
                    } else {
                        Log.e("Validation", "Invalid date conversion.")
                    }
                } else {
                    Log.d("Validation", "Dates do not match the format.")
                }
            }
        }

        // Attach validation watcher
        startDateField.addTextChangedListener(validationWatcher)
        endDateField.addTextChangedListener(validationWatcher)
    }




    private fun setupStudentAdapter() {
        val rvStudentList: RecyclerView = findViewById(R.id.rvStudents)

        Log.d("RecyclerView", "Initial student list size: ${studentInfo.size}")

        studentListAdapter = StudentAdapter(studentInfo, this)
        rvStudentList.apply {
            adapter = studentListAdapter
            layoutManager = LinearLayoutManager(this@MapActivity)
        }
    }

    private fun fitCameraToPoints() {
        if (!mapReady || recentPointsMap.isEmpty()) {
            return
        }

        // Collect all points from all IDs
        val allPoints = recentPointsMap.values.flatMap { it }

        if (allPoints.isEmpty()) {
            return
        }

        // Build bounds to include all points
        val boundsBuilder = LatLngBounds.Builder()
        allPoints.forEach { markerPoint ->
            boundsBuilder.include(markerPoint.point)
        }

        try {
            // Create bounds that include all points
            val bounds = boundsBuilder.build()

            // Animate camera to show all points with some padding
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,  // The bounds to show
                    100     // Padding in pixels
                )
            )
        } catch (e: IllegalStateException) {
            Log.e("MapCamera", "Error fitting camera to points", e)
        }
    }

    private fun testpoints(){
        dbHelper.deleteDb()
        val now = System.currentTimeMillis() / 1000
        val then = now - 240
        val between = now - 120
        processPayload("ID: 816033593, LAT: 10.640947, LONG: -61.402638, TIMESTAMP: $now")
        processPayload("ID: 816033593, LAT: 10.639731, LONG: -61.402749, TIMESTAMP: $then")
        processPayload("ID: 816033593, LAT: 10.640192, LONG: -61.402694, TIMESTAMP: $between")
        processPayload("ID: 816036904, LAT: 10.638610, LONG: -61.400561, TIMESTAMP: $now")
        processPayload("ID: 816036904, LAT: 10.639536, LONG: -61.399385, TIMESTAMP: $then")

        dbHelper.logAllData()
    }

    fun getColorForId(id: Int): Int {
        // Hash the ID to get a consistent value
        val hash = id.hashCode()

        // Convert the hash value to a color
        val red = (hash shr 16) and 0xFF // Extract the red component from the hash
        val green = (hash shr 8) and 0xFF // Extract the green component from the hash
        val blue = hash and 0xFF // Extract the blue component from the hash

        // Return the color with full alpha (255)
        return Color.argb(255, red, green, blue)
    }

    fun getHueFromColor(color: Int): Float {
        // Convert the color to HSV
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        // Extract the Hue (first value in the HSV array)
        return hsv[0]
    }





}

data class StudentInfo(val id: Int, var maxSpd: Double, var minSpd: Double, var avgSpd: Double, var totalEntries: Int) {

}

// Data class to represent a custom marker point
data class CustomMarkerPoints(val id: Int, val point: LatLng, val time: Long)
