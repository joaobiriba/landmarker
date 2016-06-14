package io.snapback.magicwhand;

import android.app.Application;
import android.util.Log;

import io.snapback.magicwhand.R;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * setup fonts and shit
 */
public class LandmarkerApplication extends Application
{
    private static final String TAG = LandmarkerApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "oh nooo");

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                        .setDefaultFontPath("fonts/texgyreheros-bold.otf")
                        .setFontAttrId(R.attr.fontPath)
                        .build()
        );
    }
}
