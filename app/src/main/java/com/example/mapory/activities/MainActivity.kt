package com.example.mapory.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mapory.R
import com.example.mapory.databinding.ActivityMainBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.mapory.models.WeatherResponse
import com.example.mapory.network.WeatherService
import com.example.mapory.utils.Constants
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.jvm.java


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isFabOpen = false
    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    // Floating Button Animations
    private lateinit var rotateOpen: Animation
    private lateinit var rotateClose: Animation
    private lateinit var fromBottom: Animation
    private lateinit var toBottom: Animation

    private lateinit var fabAddHappyPlace: FloatingActionButton
    private lateinit var addPlace: ExtendedFloatingActionButton
    private lateinit var memoryPlaces: ExtendedFloatingActionButton


    private var isFabMenuOpen = false

    @SuppressLint("MissingInflatedId")
    companion object {
        var ADD_PLACE_ACTIVITY_REQUEST_CODE = 1
        var EXTRA_PLACE_DETAILS = "extra_place_details"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()
        // Floating Button Intialization
        rotateOpen = AnimationUtils.loadAnimation(this, R.anim.rotate_open)
        rotateClose = AnimationUtils.loadAnimation(this, R.anim.rotate_close)
        fromBottom = AnimationUtils.loadAnimation(this, R.anim.from_bottom)
        toBottom = AnimationUtils.loadAnimation(this, R.anim.to_bottom)
        fabAddHappyPlace = findViewById(R.id.fabAddHappyPlace)
        addPlace = findViewById(R.id.addPlace)
        memoryPlaces = findViewById(R.id.memoryPlaces)
        fabAddHappyPlace.setOnClickListener {
            if (isFabMenuOpen) {
                closeFabMenu()
            } else {
                openFabMenu()
            }
        }
            // Customize SwipeRefreshLayout appearance (optional)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary_text_color, R.color.secondary_text_color)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(android.R.color.white)
        val toolbar = findViewById<Toolbar>(R.id.toolbar_add_place)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = "Mapory"
        // Set up SwipeRefreshLayout listener
        binding.swipeRefreshLayout.setOnRefreshListener {
            if (isLocationEnabled()) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    requestLocationData()
                    // Timeout to stop refresh animation if location or API takes too long
                    handler.postDelayed({
                        if (binding.swipeRefreshLayout.isRefreshing) {
                            binding.swipeRefreshLayout.isRefreshing = false
                            Toast.makeText(
                                this,
                                "Refresh timed out. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }, 10000) // 10-second timeout
                } else {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(
                        this,
                        "Location permissions are required. Please enable them.",
                        Toast.LENGTH_SHORT
                    ).show()
                    showRationalDialogForPermissions()
                }
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(
                    this,
                    "Your location provider is turned OFF. Please turn it on.",
                    Toast.LENGTH_SHORT
                ).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        }
        // Initial location check and permission request
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned OFF. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }
                    }
                    override fun onPermissionRationaleShouldBeShown(
                        p0: List<PermissionRequest?>?,
                        p1: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
        val addPlaceFab = findViewById<ExtendedFloatingActionButton>(R.id.addPlace)
        addPlaceFab.text = "Add A Memory"
        addPlaceFab.setTextColor(Color.WHITE)
        addPlaceFab.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))

        val icon = ContextCompat.getDrawable(this, R.drawable.ic_add_place_24)
        addPlaceFab.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null)

        binding.fabAddHappyPlace.setOnClickListener {
            setFabVisibility(isFabOpen)
            setFabAnimation(isFabOpen)
            isFabOpen = !isFabOpen
        }
        binding.addPlace.setOnClickListener {
            val intent = Intent(this, AddHappyPlaceActivity::class.java)
            startActivityForResult(intent, ADD_PLACE_ACTIVITY_REQUEST_CODE)
        }
        binding.memoryPlaces.setOnClickListener {
            val intent = Intent(this, MapOfMemory::class.java)
            startActivity(intent)
        }
    }


private fun openFabMenu() {
    addPlace.visibility = View.VISIBLE
    memoryPlaces.visibility = View.VISIBLE
    isFabMenuOpen = true
}

private fun closeFabMenu() {
    addPlace.visibility = View.GONE
    memoryPlaces.visibility = View.GONE
    isFabMenuOpen = false
}

    private fun setFabVisibility(isOpen: Boolean) {
        if (!isOpen) {
            binding.addPlace.visibility = View.VISIBLE
            binding.memoryPlaces.visibility = View.VISIBLE
        } else {
            binding.addPlace.visibility = View.GONE
            binding.memoryPlaces.visibility = View.GONE
        }
    }

    private fun setFabAnimation(isOpen: Boolean) {
        if (!isOpen) {
            binding.addPlace.startAnimation(fromBottom)
            binding.memoryPlaces.startAnimation(fromBottom)
            binding.fabAddHappyPlace.startAnimation(rotateOpen)
        } else {
            binding.addPlace.startAnimation(toBottom)
            binding.memoryPlaces.startAnimation(toBottom)
            binding.fabAddHappyPlace.startAnimation(rotateClose)
        }
    }
    private fun getLocationWeatherDetails(latitude: Double?, longitude: Double?) {
        if (Constants.isNetworkAvailable(this)) {
            val apiKey = Constants.getAppId(this)
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service: WeatherService = retrofit.create(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, apiKey
            )
            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse?>, response: Response<WeatherResponse?>) {
                    hideProgressDialog()
                    binding.swipeRefreshLayout.isRefreshing = false // Stop refresh animation
                    if (response.isSuccessful) {
                        val weatherList: WeatherResponse? = response.body()
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        // Save to SharedPreferences
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        // Update the UI
                        setupUI()
                        Log.i("Response Result", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> Log.e("Error 400", "Bad Connection")
                            404 -> Log.e("Error 404", "Not Found")
                            else -> Log.e("Error", "Generic Error")
                        }
                        Toast.makeText(this@MainActivity,
                            "Failed to fetch weather data. Error Code : $rc",
                            Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                override fun onFailure(call: Call<WeatherResponse?>, t: Throwable) {
                    hideProgressDialog()
                    binding.swipeRefreshLayout.isRefreshing = false // Stop refresh animation
                    Log.e("Error", t.message.toString())
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to fetch weather data. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        } else {
            hideProgressDialog()
            binding.swipeRefreshLayout.isRefreshing = false // Stop refresh animation
            Toast.makeText(
                this@MainActivity,
                "You are not connected to the internet.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1 // Request only one location update
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.getMainLooper()
        )
    }
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            val longitude = mLastLocation?.longitude
            Log.i("Current Location", "Lat: $latitude, Lon: $longitude")
            // Stop location updates after receiving one result
            mFusedLocationClient.removeLocationUpdates(this)
            getLocationWeatherDetails(latitude, longitude)

        }
    }
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }
    private fun setupUI() {
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for (z in weatherList.weather.indices) {
                Log.i("Weather Nameeeeee", weatherList.weather[z].main)
                binding.tvMain.text = weatherList.weather[z].main
                binding.tvMainDescription.text = weatherList.weather[z].description
                binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
                binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
                binding.tvSpeed.text = weatherList.wind.speed.toString()
                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country
                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)
                when (weatherList.weather[z].icon) {
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                }
            }
        }
//        else {
//            // Handle empty data case (e.g., show default values or a message)
//            binding.tvMain.text = "N/A"
//            binding.tvMainDescription.text = "N/A"
//            binding.tvTemp.text = "N/A"
//            binding.tvHumidity.text = "N/A"
//            binding.tvMin.text = "N/A"
//            binding.tvMax.text = "N/A"
//            binding.tvSpeed.text = "N/A"
//            binding.tvName.text = "N/A"
//            binding.tvCountry.text = "N/A"
//            binding.tvSunriseTime.text = "N/A"
//            binding.tvSunsetTime.text = "N/A"
//            binding.ivMain.setImageResource(R.drawable.cloud) // Default image
//        }
        binding.swipeRefreshLayout.isRefreshing = false // Ensure refresh stops
    }
    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat")
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
    private fun getUnit(value: String): String {
        Log.i("Temperature Unit", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }
    private fun isLocationEnabled() : Boolean {
        val locationManager : LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Clean up location updates and handler callbacks
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
        handler.removeCallbacksAndMessages(null)
    }
}


