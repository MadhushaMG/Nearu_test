package com.codara.nearutest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mapbox.common.MapboxOptions;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentUtils;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 101;
    private static final String MAPBOX_TOKEN = "pk.eyJ1IjoiYXBwbmVhcnUiLCJhIjoiY20wNmgzZGplMDRzNTJqcHhycndvMW4yaiJ9.6J3gw8SOlGAggjawShuaIg";

    private MapView mapView;
    private TextView tvSpeed, tvGpsStatus, tvMaxSpeed, tvAccuracy, tvAltitude, tvAddress;
    private CardView btnRecenter, btnZoomIn, btnZoomOut;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;
    private boolean autoFollow = true;
    private float maxSpeed = 0f;
    private double currentZoom = 16.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status bar transparent
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        MapboxOptions.INSTANCE.setAccessToken(MAPBOX_TOKEN);
        setContentView(R.layout.activity_main);

        mapView     = findViewById(R.id.mapView);
        tvSpeed     = findViewById(R.id.tvSpeed);
        tvGpsStatus = findViewById(R.id.tvGpsStatus);
        tvMaxSpeed  = findViewById(R.id.tvMaxSpeed);
        tvAccuracy  = findViewById(R.id.tvAccuracy);
        tvAltitude  = findViewById(R.id.tvAltitude);
        tvAddress   = findViewById(R.id.tvAddress);
        btnRecenter = findViewById(R.id.btnRecenter);
        btnZoomIn   = findViewById(R.id.btnZoomIn);
        btnZoomOut  = findViewById(R.id.btnZoomOut);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Dynamic insets - top bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(
                    v.getPaddingLeft(),
                    statusBarHeight + 14,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets;
        });

        // Dynamic insets - bottom speed panel
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.speedPanel), (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    navBarHeight + 12
            );
            return insets;
        });

        // Buttons
        btnRecenter.setOnClickListener(v -> {
            autoFollow = true;
            if (lastLocation != null) moveCameraTo(lastLocation, currentZoom);
        });

        btnZoomIn.setOnClickListener(v -> {
            currentZoom = Math.min(currentZoom + 1, 20.0);
            if (lastLocation != null) moveCameraTo(lastLocation, currentZoom);
        });

        btnZoomOut.setOnClickListener(v -> {
            currentZoom = Math.max(currentZoom - 1, 5.0);
            if (lastLocation != null) moveCameraTo(lastLocation, currentZoom);
        });

        // Location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;
                lastLocation = location;

                float speedKmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
                int speedInt = (int) speedKmh;

                if (speedKmh > maxSpeed) maxSpeed = speedKmh;

                runOnUiThread(() -> {
                    tvSpeed.setText(String.valueOf(speedInt));

                    if (speedInt == 0) {
                        tvSpeed.setTextColor(0xFFFFFFFF);
                    } else if (speedInt < 60) {
                        tvSpeed.setTextColor(0xFF4FC3F7);
                    } else if (speedInt < 100) {
                        tvSpeed.setTextColor(0xFFFFAA00);
                    } else {
                        tvSpeed.setTextColor(0xFFFF4444);
                    }

                    tvMaxSpeed.setText("MAX  " + (int) maxSpeed + " km/h");
                    tvAccuracy.setText("ACC  " + (int) location.getAccuracy() + " m");
                    tvAltitude.setText("ALT  " + (int) location.getAltitude() + " m");

                    tvGpsStatus.setText("⬤ GPS");
                    tvGpsStatus.setTextColor(0xFF44FF44);
                });

                if (autoFollow) moveCameraTo(location, currentZoom);

                getAddressFromLocation(location.getLatitude(), location.getLongitude());
            }
        };

        // Map load
        mapView.getMapboxMap().loadStyleUri(Style.SATELLITE_STREETS, style -> {
            Log.d(TAG, "Map loaded");
            checkPermissionsAndEnable();
        }, error -> {
            Log.e(TAG, "Map error: " + error.getMessage());
            runOnUiThread(() -> {
                tvGpsStatus.setText("✕ Map Error");
                tvGpsStatus.setTextColor(0xFFFF4444);
            });
        });
    }

    private void moveCameraTo(Location location, double zoom) {
        CameraOptions camera = new CameraOptions.Builder()
                .center(Point.fromLngLat(location.getLongitude(), location.getLatitude()))
                .zoom(zoom)
                .bearing((double) location.getBearing())
                .build();
        mapView.getMapboxMap().setCamera(camera);
    }

    private void getAddressFromLocation(double lat, double lng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    String road = addr.getThoroughfare();
                    String city = addr.getLocality();
                    String display = (road != null ? road : "") +
                            (city != null ? (road != null ? ", " : "") + city : "");
                    if (!display.isEmpty()) {
                        runOnUiThread(() -> tvAddress.setText(display));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Geocoder error: " + e.getMessage());
            }
        }).start();
    }

    private void checkPermissionsAndEnable() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_PERMISSION_REQUEST);
        } else {
            enableLocationComponent();
            startLocationUpdates();
        }
    }

    private void enableLocationComponent() {
        try {
            LocationComponentPlugin lc = LocationComponentUtils.getLocationComponent(mapView);
            lc.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "enableLocationComponent: " + e.getMessage());
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        lastLocation = location;
                        moveCameraTo(location, currentZoom);
                        getAddressFromLocation(location.getLatitude(), location.getLongitude());
                        Log.d(TAG, "Initial fix: " + location.getLatitude()
                                + ", " + location.getLongitude());
                    }
                });

        LocationRequest req = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(1f)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, getMainLooper());
        } catch (Exception e) {
            Log.e(TAG, "requestLocationUpdates: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocationComponent();
            startLocationUpdates();
        } else {
            runOnUiThread(() -> {
                tvGpsStatus.setText("✕ No Permission");
                tvGpsStatus.setTextColor(0xFFFF4444);
            });
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
        if (fusedLocationClient != null && locationCallback != null)
            fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
}