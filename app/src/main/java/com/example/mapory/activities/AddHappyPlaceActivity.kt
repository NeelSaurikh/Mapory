package com.example.mapory.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.mapory.R
import com.example.mapory.database.DatabaseHandler
import com.example.mapory.databinding.ActivityAddHappyPlaceBinding
import com.example.mapory.models.HappyPlaceModel
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AddHappyPlaceActivity : AppCompatActivity(), View.OnClickListener {

    private var cal = Calendar.getInstance()
    private lateinit var dateSetListener: DatePickerDialog.OnDateSetListener
    private lateinit var binding: ActivityAddHappyPlaceBinding
    private var saveImageToInternalStorage: Uri? = null
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0
    private var mHappyPlacesDetails: HappyPlaceModel? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    // Activity Result Launchers
    @RequiresApi(Build.VERSION_CODES.Q)
    private val galleryResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val contentURI = data.data
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        contentResolver.loadThumbnail(contentURI!!, android.util.Size(300, 300), null)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, contentURI)
                    }
                    saveImageToInternalStorage = saveImageToInternalStorage(bitmap)
                    Log.i("Saved image:", "Path:: $saveImageToInternalStorage")
                    binding.ivPlaceImage.setImageBitmap(bitmap)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to load image!", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Log.i("Cancelled", "Gallery selection cancelled")
        }
    }

    private val cameraResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val thumbnail: Bitmap = data.extras?.get("data") as Bitmap
                saveImageToInternalStorage = saveImageToInternalStorage(thumbnail)
                Log.i("Saved image:", "Path:: $saveImageToInternalStorage")
                binding.ivPlaceImage.setImageBitmap(thumbnail)
            } else {
                Toast.makeText(this, "Failed to capture image!", Toast.LENGTH_SHORT).show()
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Log.i("Cancelled", "Camera capture cancelled")
        }
    }

    private val autocompleteResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val place = Autocomplete.getPlaceFromIntent(data)
                binding.etLocation.setText(place.address)
                mLatitude = place.latLng?.latitude ?: 0.0
                mLongitude = place.latLng?.longitude ?: 0.0
                Log.i("Place Selected", "Address: ${place.address}, Lat: $mLatitude, Lng: $mLongitude")
            }
        } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
            val status = Autocomplete.getStatusFromIntent(result.data!!)
            Log.e("Autocomplete", "Error: ${status.statusMessage}")
            Toast.makeText(this, "Error selecting place", Toast.LENGTH_SHORT).show()
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.i("Autocomplete", "Cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddHappyPlaceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarAddPlace)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarAddPlace.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (!Places.isInitialized()) {
            Places.initialize(this, resources.getString(R.string.google_maps_api_key))
        }
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)) {
            mHappyPlacesDetails = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS, HappyPlaceModel::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS)
            }
        }

        dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, monthOfYear)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateInView()
        }
        updateDateInView()

        if (mHappyPlacesDetails != null) {
            supportActionBar?.title = "Edit Happy Place"
            binding.etTitle.setText(mHappyPlacesDetails!!.title)
            binding.etDescription.setText(mHappyPlacesDetails!!.description)
            binding.etDate.setText(mHappyPlacesDetails!!.date)
            binding.etLocation.setText(mHappyPlacesDetails!!.location)
            mLatitude = mHappyPlacesDetails!!.latitude
            mLongitude = mHappyPlacesDetails!!.longitude
            saveImageToInternalStorage = Uri.parse(mHappyPlacesDetails!!.image)
            binding.ivPlaceImage.setImageURI(saveImageToInternalStorage)
            binding.btnSave.text = "UPDATE"
        }

        binding.etDate.setOnClickListener(this)
        binding.tvAddImage.setOnClickListener(this)
        binding.btnSave.setOnClickListener(this)
        binding.etLocation.setOnClickListener(this)
        binding.tvSelectCurrentLocation.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.et_date -> {
                DatePickerDialog(
                    this,
                    dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            R.id.tv_add_image -> {
                val pictureDialog = AlertDialog.Builder(this)
                pictureDialog.setTitle("Select Action")
                val pictureDialogItems = arrayOf("Select photo from gallery", "Capture photo from camera")
                pictureDialog.setItems(pictureDialogItems) { _, which ->
                    when (which) {
                        0 -> choosePhotoFromGallery()
                        1 -> takePhotoFromCamera()
                    }
                }
                pictureDialog.show()
            }
            R.id.btn_save -> {
                when {
                    binding.etTitle.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please Enter Title", Toast.LENGTH_SHORT).show()
                    }
                    binding.etDescription.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please Enter A Description", Toast.LENGTH_SHORT).show()
                    }
                    binding.etLocation.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please Enter A Location", Toast.LENGTH_SHORT).show()
                    }
                    saveImageToInternalStorage == null -> {
                        Toast.makeText(this, "Please Select An Image", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val happyPlaceModel = HappyPlaceModel(
                            if (mHappyPlacesDetails == null) 0 else mHappyPlacesDetails!!.id,
                            binding.etTitle.text.toString(),
                            saveImageToInternalStorage.toString(),
                            binding.etDescription.text.toString(),
                            binding.etDate.text.toString(),
                            binding.etLocation.text.toString(),
                            mLatitude,
                            mLongitude
                        )
                        val dbHandler = DatabaseHandler(this)
                        if (mHappyPlacesDetails == null) {
                            val addHappyPlace = dbHandler.addHappyPlace(happyPlaceModel)
                            if (addHappyPlace > 0) {
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        } else {
                            val updateHappyPlace = dbHandler.updateHappyPlace(happyPlaceModel)
                            if (updateHappyPlace > 0) {
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        }
                    }
                }
            }
            R.id.et_location -> {
                try {
                    val fields = listOf(
                        Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS
                    )
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                        .build(this)
                    autocompleteResultLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e("Autocomplete", "Error launching autocomplete", e)
                    Toast.makeText(this, "Error launching place picker", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.tv_select_current_location -> {
                if (!isLocationEnabled()) {
                    Toast.makeText(this, "Please enable location services.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                } else {
                    Dexter.withContext(this)
                        .withPermissions(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        .withListener(object : MultiplePermissionsListener {
                            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                                if (report?.areAllPermissionsGranted() == true) {
                                    requestNewLocationData()
                                } else {
                                    Toast.makeText(
                                        this@AddHappyPlaceActivity,
                                        "Location permissions are required.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            override fun onPermissionRationaleShouldBeShown(
                                permissions: MutableList<PermissionRequest>?,
                                token: PermissionToken?
                            ) {
                                showRationalDialogForPermissions()
                            }
                        }).onSameThread().check()
                }
            }
        }
    }

    private fun updateDateInView() {
        val myFormat = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        binding.etDate.setText(sdf.format(cal.time))
    }

    private fun choosePhotoFromGallery() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        Dexter.withActivity(this)
            .withPermissions(*permissions)
            .withListener(object : MultiplePermissionsListener {
                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report?.areAllPermissionsGranted() == true) {
                        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        galleryResultLauncher.launch(galleryIntent)
                    } else {
                        Toast.makeText(
                            this@AddHappyPlaceActivity,
                            "Storage permissions are required to access the gallery.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread().check()
    }

    private fun takePhotoFromCamera() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        Dexter.withActivity(this)
            .withPermissions(*permissions.toTypedArray())
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report?.areAllPermissionsGranted() == true) {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        cameraResultLauncher.launch(intent)
                    } else {
                        Toast.makeText(
                            this@AddHappyPlaceActivity,
                            "Camera and storage permissions are required.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread().check()
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Unable to open settings.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri {
        val wrapper = ContextWrapper(applicationContext)
        var file = wrapper.getDir(IMAGE_DIRECTORY, MODE_PRIVATE)
        file = File(file, "${UUID.randomUUID()}.jpg")

        try {
            val stream: OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return Uri.parse(file.absolutePath)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1)
            .build()

        mFusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    mLatitude = location.latitude
                    mLongitude = location.longitude
                    Log.i("Current Location", "Lat: $mLatitude, Lng: $mLongitude")

                    val geocoder = Geocoder(this@AddHappyPlaceActivity, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(mLatitude,mLongitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0].getAddressLine(0)
                            binding.etLocation.setText(address)
                        } else {
                            binding.etLocation.setText("Unknown Location")
                        }
                    } catch (e: IOException) {
                        Log.e("Geocoder", "Error getting address", e)
                        binding.etLocation.setText("Error fetching address")
                    }
                }
            },
            Looper.getMainLooper()
        )
    }

    companion object {
        private const val IMAGE_DIRECTORY = "HappyPlacesImages"
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 3
    }
}