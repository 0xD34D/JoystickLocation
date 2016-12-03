package com.scheffsblend.joysticklocation;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import java.util.Random;

public class MockLocationProvider {
    private static final float ACCURACY_VARIANCE = 10.0f;
    String providerName;
    String[] providerNames = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER};
    Context ctx;
    Random mRandom;
    float mLastAccuracy;

    public MockLocationProvider(String name, Context ctx) {
        this.providerName = name;
        this.ctx = ctx;
        mRandom = new Random();
        mLastAccuracy = 20;

        LocationManager lm = (LocationManager) ctx.getSystemService(
                Context.LOCATION_SERVICE);
        for (String provider : providerNames) {
            lm.addTestProvider(provider, false, false, false, false, false,
                    true, true, 0, 50);
            lm.setTestProviderEnabled(provider, true);
        }
    }

    public void pushLocation(double lat, double lon, float bearing) {
        LocationManager lm = (LocationManager) ctx.getSystemService(
                Context.LOCATION_SERVICE);

        for (String provider : providerNames) {
            Location mockLocation = new Location(provider);
            mockLocation.setLatitude(lat);
            mockLocation.setLongitude(lon);
            mockLocation.setAltitude(0);
            mockLocation.setBearing(bearing);
            mockLocation.setTime(System.currentTimeMillis());
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            if (mRandom.nextInt(10) == 0) {
                mLastAccuracy = 20;// + mRandom.nextFloat() * ACCURACY_VARIANCE * 2 - ACCURACY_VARIANCE;
            }
            mockLocation.setAccuracy(provider.equals(LocationManager.GPS_PROVIDER)
                    ? mLastAccuracy
                    : 1500);
            lm.setTestProviderLocation(provider, mockLocation);
        }
    }

    public void shutdown() {
        LocationManager lm = (LocationManager) ctx.getSystemService(
                Context.LOCATION_SERVICE);
        lm.removeTestProvider(providerName);
    }
}