package com.scheffsblend.joysticklocation;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;

import java.util.Random;

/**
 * Created by clark on 7/20/2016.
 */
public class JoystickOverlayService extends Service implements LocationListener, OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks {

    private static final long UPDATE_DURATION = 250L;
    private static final long UPDATE_DURATION_STATIONARY = 1000L;
    private static final double MAX_SPEED_FACTOR = 0.000015;
    private static final String KEY_LAST_LATITUDE = "last_known_latitude";
    private static final String KEY_LAST_LONGITUDE = "last_known_longitude";

    private View mOverlay;
    private CheckBox mSnapBack;
    private WindowManager.LayoutParams mOverlayParams;
    private WindowManager mWindowManager;
    private LocationManager mLocationManager;
    private MapView mMapView;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private boolean mJumpToLocation = true;

    private float mStartTouchPointX;
    private float mStartTouchPointY;

    private Location mCurrentLocation;
    private Location mLastLocation;

    private MockLocationProvider mMockLocationProvider;
    private Handler mHandler;
    private double mLongitudeSpeed;
    private double mLatitudeSpeed;

    private Random mRandom;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        mHandler = new Handler();

        LayoutInflater inflater = getSystemService(LayoutInflater.class);
        mOverlay = inflater.inflate(R.layout.joystick_mapview_overlay, null, false);
        mLocationManager = getSystemService(LocationManager.class);
        mMapView = (MapView) mOverlay.findViewById(R.id.map_view);
        mMapView.onCreate(null);
        mMapView.onResume();
        mMapView.getMapAsync(this);

        final JoystickView jv = (JoystickView) mOverlay.findViewById(R.id.joystick);
        jv.setOnJoystickPositionChangedListener(mListener);

        mSnapBack = (CheckBox) mOverlay.findViewById(R.id.snap_back);
        mSnapBack.setChecked(jv.getSnapBackToCenter());
        mSnapBack.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                jv.setSnapBackToCenter(isChecked);
            }
        });

        int overlayWidth = getResources().getDimensionPixelSize(R.dimen.overlay_width);
        int overlayHeight = getResources().getDimensionPixelSize(R.dimen.overlay_height);
        mOverlayParams =  new WindowManager.LayoutParams(overlayWidth, overlayHeight,
                WindowManager.LayoutParams.TYPE_TOAST,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH, PixelFormat.TRANSPARENT);
        mOverlayParams.gravity = Gravity.TOP | Gravity.LEFT;

        mWindowManager = getSystemService(WindowManager.class);
        mWindowManager.addView(mOverlay, mOverlayParams);

        View windowMover = mOverlay.findViewById(R.id.window_mover);
        windowMover.setOnTouchListener(mMoveWindowTouchListener);

        View cancelButton = mOverlay.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });
        mRandom = new Random();

        mMockLocationProvider = new MockLocationProvider(LocationManager.GPS_PROVIDER, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
        if (mOverlay.isAttachedToWindow()) {
            mWindowManager.removeView(mOverlay);
        }
        mGoogleApiClient.disconnect();
        mMockLocationProvider.shutdown();
        mHandler.removeCallbacks(mUpdateLocationRunnable);

        if (mCurrentLocation != null) {
            SharedPreferences.Editor edit =
                    PreferenceManager.getDefaultSharedPreferences(this).edit();
            edit.putString(KEY_LAST_LATITUDE, "" + mCurrentLocation.getLatitude());
            edit.putString(KEY_LAST_LONGITUDE, "" + mCurrentLocation.getLongitude());
            edit.commit();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mMap != null && location != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15.5f);
            if (mJumpToLocation) {
                mMap.moveCamera(cameraUpdate);
                mJumpToLocation = false;
            } else {
                mMap.stopAnimation();
                mMap.animateCamera(cameraUpdate, (int) UPDATE_DURATION, null);
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    private View.OnTouchListener mMoveWindowTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mStartTouchPointX = event.getRawX() - mOverlayParams.x;
                    mStartTouchPointY = event.getRawY() - mOverlayParams.y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    mOverlayParams.x = (int)(event.getRawX() - mStartTouchPointX);
                    mOverlayParams.y = (int)(event.getRawY() - mStartTouchPointY);
                    if (mOverlayParams.x < 0) mOverlayParams.x = 0;
                    if (mOverlayParams.y < 0) mOverlayParams.y = 0;
                    mWindowManager.updateViewLayout(mOverlay, mOverlayParams);
                    break;
            }
            return true;
        }
    };

    private JoystickView.OnJoystickPositionChangedListener mListener =
            new JoystickView.OnJoystickPositionChangedListener() {
        @Override
        public void onJoystickPositionChanged(float x, float y) {
            if (mCurrentLocation != null) {
                if (x != 0.0 || y != 0.0) {
                    double theta = Math.atan2(-y, x);
                    double magnitude = Math.sqrt(x*x + y*y) * MAX_SPEED_FACTOR;
                    mLatitudeSpeed = magnitude * Math.sin(theta);
                    mLongitudeSpeed = magnitude * Math.cos(theta);
                } else {
                    mLatitudeSpeed = mLongitudeSpeed = 0;
                }
            }
        }
    };

    private Runnable mUpdateLocationRunnable = new Runnable() {
        @Override
        public void run() {
            final Location lastLocation = mCurrentLocation != null
                    ? new Location(mCurrentLocation)
                    : null;
            double latitude = mCurrentLocation.getLatitude();
            double longitude = mCurrentLocation.getLongitude();
            long updateDuration = UPDATE_DURATION_STATIONARY;
            if (mLatitudeSpeed != 0.0 || mLongitudeSpeed != 0.0) {
                latitude += mLatitudeSpeed;
                longitude += mLongitudeSpeed;
                mCurrentLocation.setLatitude(latitude);
                mCurrentLocation.setLongitude(longitude);
                onLocationChanged(mCurrentLocation);
                updateDuration = UPDATE_DURATION;
            }
            latitude += mRandom.nextDouble() * MAX_SPEED_FACTOR - MAX_SPEED_FACTOR / 2;
            longitude += mRandom.nextDouble() * MAX_SPEED_FACTOR - MAX_SPEED_FACTOR / 2;
            mMockLocationProvider.pushLocation(latitude, longitude,
                    lastLocation != null ? lastLocation.bearingTo(mCurrentLocation) : 0f);
            mHandler.postDelayed(this, updateDuration);
        }
    };

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Location location = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (location == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String latStr = prefs.getString(KEY_LAST_LATITUDE, null);
            String lngStr = prefs.getString(KEY_LAST_LONGITUDE, null);
            if (latStr != null && lngStr != null) {
                location = new Location(LocationManager.GPS_PROVIDER);
                location.setLatitude(Double.valueOf(latStr));
                location.setLongitude(Double.valueOf(lngStr));
                location.setTime(System.currentTimeMillis());
                location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }
        }
        if (location != null) {
            mCurrentLocation = location;
            onLocationChanged(location);
            mHandler.post(mUpdateLocationRunnable);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
