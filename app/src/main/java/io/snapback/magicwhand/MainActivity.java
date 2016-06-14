package io.snapback.magicwhand;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.snapback.magicwhand.data.NearbyPlace;
import io.snapback.magicwhand.data.Place;
import io.snapback.magicwhand.sensors.HeadTracker;
import io.snapback.magicwhand.util.HeadTransform;
import io.snapback.magicwhand.util.InclinationDetector;
import io.snapback.magicwhand.widget.DirectionalTextViewContainer;
import io.snapback.magicwhand.widget.IntroView;
import io.snapback.magicwhand.widget.SwingPhoneView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;
import se.walkercrou.places.GooglePlaces;


public class MainActivity extends BaseActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    //go to https://code.google.com/apis/console to register an app and get a key!
    private static final String PLACES_API_KEY = "AIzaSyAAkrlC3TfTJ5HCyORSPafyj3em_4cJLJ8";

    private static final String STATE_RESOLVING_ERROR = "resolving_error";

    private static final double MAX_RADIUS = 50000;

    private static final int REQUEST_CHECK_SETTINGS = 100;

    /**
     * attempts at finding a location with decent accuracy
     */
    private static final int MAX_UPDATE_TRIES = 5;

    /**
     * if a location is older than an hour, try and get a new one
     */
    private static final int MIN_AGE_IN_HOURS = 1;

    private GoogleApiClient mGoogleApiClient;

    private boolean mResolvingError = false;

    private Location mLastLocation;
    private GooglePlaces mPlacesApi;

    @InjectView(io.snapback.magicwhand.R.id.intro_view)
    IntroView mIntroView;
    @InjectView(io.snapback.magicwhand.R.id.swing_phone_view)
    SwingPhoneView mSwingPhoneView;

    @InjectView(io.snapback.magicwhand.R.id.operas_view)
    LinearLayout mOperasView;
    @InjectView(io.snapback.magicwhand.R.id.main_opera)
    ImageView mMainOperaIV;
    @InjectView(io.snapback.magicwhand.R.id.main_opera_confidence_tv)
    TextView mMainOperaTV;
    @InjectView(io.snapback.magicwhand.R.id.second_opera)
    ImageView mSecondOperaIV;
    @InjectView(io.snapback.magicwhand.R.id.second_opera_confidence_tv)
    TextView mSecondOperaTV;
    @InjectView(io.snapback.magicwhand.R.id.third_opera)
    ImageView mThirdOperaIV;
    @InjectView(io.snapback.magicwhand.R.id.third_opera_confidence_tv)
    TextView mThirdOperaTV;


    @InjectView(io.snapback.magicwhand.R.id.directional_text_view_container)
    DirectionalTextViewContainer mDirectionalTextViewContainer;
    @InjectView(io.snapback.magicwhand.R.id.maps_button_view_container)
    View mMapsButtonViewContainer;

    private NearbyPlace mCurrentPlace;

    private boolean mIsFirstRun = true;
    private boolean mIsConnectedToGApi = false;
    private boolean mIsReadyToCheckLastLocation = false;
    private LocationRequest mLocationReq;

    private HeadTracker mHeadTracker;
    private HeadTransform mHeadTransform;
    private Handler mTrackingHandler = new Handler();
    private boolean mIsTracking = false;
    private float[] mEulerAngles = new float[3];

    private boolean mHasPlaces = false;

    Context context;
    //Detector of inclination
    private InclinationDetector inclinationDetector;


    //Handler to read inclination and associated thread
    private Handler motionHandler;
    private HandlerThread motionHandlerThread;


    //Event expressing gestures
    private static final int MSG_START = 1;
    private static final int MSG_STOP = 3;
    private static final int MSG_STATE_IDLE = 10;
    private static final int MSG_STATE_COUPLING_TRACK = 20;
    private static final int MSG_STATE_GESTURE_TRACK = 30;
    private static final int MSG_STATE_GESTURE_PAUSE_STOP_TRACK = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(io.snapback.magicwhand.R.layout.activity_main);

        context = this;

        mResolvingError = savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
        mHasPlaces = false;

        initViews();
        initSensors();

        buildGoogleApiClient();
        buildPlacesApi();

        inclinationDetector = new InclinationDetector(getApplicationContext());

        motionHandlerThread = new HandlerThread(getClass().getSimpleName());
        motionHandlerThread.start();
        motionHandler = new Handler(motionHandlerThread.getLooper(),
                new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        return MainActivity.this.handleMessage(msg);
                    }
                });
    }

    private void initViews() {
        ButterKnife.inject(this);

        mSwingPhoneView.setVisibility(View.GONE);
        mDirectionalTextViewContainer.setVisibility(View.GONE);
        mOperasView.setVisibility(View.GONE);
    }

    private void initSensors() {
        mHeadTracker = HeadTracker.createFromContext(this);
        mHeadTransform = new HeadTransform();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void buildPlacesApi() {
        mPlacesApi = new GooglePlaces(PLACES_API_KEY);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!mResolvingError && !mGoogleApiClient.isConnected()) {  // more about this later
            Log.d(TAG, "onStart() && Api.connect()");
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //events
        EventBus.getDefault().register(this);

        //Inclination detector start
        inclinationDetector.resume();
        sendMessage(MSG_START);

        //sensors
        mHeadTracker.startTracking();

        //drawing
        mDirectionalTextViewContainer.startDrawing();

        //animateIn
        if (mIsFirstRun) {
            animateTitleIn();
            mIsFirstRun = false;
            return;
        }

        //resuming from pause/maps
        if (mHasPlaces)
            startTracking();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
    }

    @Override
    protected void onPause() {
        super.onPause();

        EventBus.getDefault().unregister(this);

        inclinationDetector.pause();
        sendMessage(MSG_STOP);

        mIsTracking = false;
        mHeadTracker.stopTracking();

        mDirectionalTextViewContainer.stopDrawing();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    //butterknife

    @OnClick(io.snapback.magicwhand.R.id.maps_button_view)
    public void onMapsButtonClick() {
        if (mCurrentPlace == null) {
            Log.w(TAG, "No currentPlace available - must be empty. Ignore click.");
            return;
        }

        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + URLEncoder.encode(mCurrentPlace.getName(), "UTF-8"))
            );
            //cheating!
            intent.setPackage("com.google.android.apps.maps");
            startActivity(intent);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @OnClick(io.snapback.magicwhand.R.id.maps_button_close)
    public void onMapsViewCloseClicked() {
        hideMapsButtonView();
    }

    @OnClick(io.snapback.magicwhand.R.id.maps_button_view_container)
    public void onContainerClick() {
        //do nothing - just need registered for onClick so it doesnt get passed through
    }

    //overrides

    @Override
    public void onBackPressed() {
        if (mMapsButtonViewContainer.getVisibility() == View.VISIBLE)
            hideMapsButtonView();
        else
            super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                //settings have been enabled, continue forward!
                setLocationListener();
            } else {
                //we need location enabled for this app to work, so exit if we can't
                Toast.makeText(
                        this,
                        "Location Services need to be enabled for app to function. Please enable and try again.",
                        Toast.LENGTH_LONG)
                        .show();

                this.finish();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    //event bus

    /**
     * handle when a place is clicked
     *
     * @param event custom EventBus event
     */
    public void onEvent(DirectionalTextViewContainer.OnPlaceClickedEvent event) {
        if (event.place == null) {
            Log.w(TAG, "ignoring because no place is currently available.");
            return;
        }

        mCurrentPlace = event.place;
        showMapsButtonView();
    }

    //private api

    private void animateTitleIn() {
        final Runnable completeRunner = new Runnable() {
            @Override
            public void run() {
                if (mIsConnectedToGApi)
                    checkLastLocation();
                else
                    mIsReadyToCheckLastLocation = true;
            }
        };

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mIntroView.animateIn(completeRunner);
                    }
                });
            }
        }, 500);
    }

    private void showMapsButtonView() {
        mMapsButtonViewContainer.setVisibility(View.VISIBLE);
        Animation anim = new AlphaAnimation(0.f, 1.f);
        anim.setDuration(300);
        mMapsButtonViewContainer.startAnimation(anim);
    }

    private void hideMapsButtonView() {
        mMapsButtonViewContainer.setVisibility(View.GONE);
        Animation anim = new AlphaAnimation(1.f, 0.f);
        anim.setDuration(300);
        mMapsButtonViewContainer.startAnimation(anim);
    }

    /**
     * method for refreshing content from Places API.
     * will check location if its latest and do as needed
     */
    private void checkLastLocation() {
        mLocationReq = new LocationRequest();
        mLocationReq.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationReq.setInterval(1000);
        mLocationReq.setFastestInterval(5000);
        mLocationReq.setNumUpdates(MAX_UPDATE_TRIES);

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation == null) {
            checkSettings();
            return;
        }

        //it exists, see how old it is

        int hours = getLocationAgeHours(mLastLocation);
        Log.d(TAG, mLastLocation + "\nHours since update: " + hours);

        if (hours > MIN_AGE_IN_HOURS) { // || seconds > 15 ) //for testing
            setLocationListener();
            return;
        }

        //location is fine, update places
        getNewPlaces();
    }

    private int getLocationAgeHours(Location loc) {
        long duration = (SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / 1000000L;
        int seconds = (int) Math.floor(duration / 1000);

//        Log.d(TAG, "getLocationAge() elapsed: " + (SystemClock.elapsedRealtimeNanos() / 1000000L)  + " location: " +  (loc.getElapsedRealtimeNanos() / 1000000L) + " seconds: " + seconds);

        return (int) Math.floor(seconds / 60 / 60);
    }

    private void checkSettings() {
        //get settings request for our location request
        LocationSettingsRequest req = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationReq)
                .build();

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, req);
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        setLocationListener();
                        break;

                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            Log.e(TAG, e.getLocalizedMessage());
                        }
                        break;

                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't animateIn the dialog.
                        break;
                }
            }
        });
    }

    private void setLocationListener() {
        Log.d(TAG, "setLocationListener() " + mLocationReq);

        PendingResult<Status> result = LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                mLocationReq,
                new LocationListener() {

                    int numTries = 0;

                    @Override
                    public void onLocationChanged(Location location) {
                        numTries++;

                        Log.d(TAG, "onLocationChanged() attempt: " + numTries + " :: " + location);

                        if (getLocationAgeHours(location) <= MIN_AGE_IN_HOURS || numTries == MAX_UPDATE_TRIES) {
                            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                            mLastLocation = location;
                            getNewPlaces();
                        }
                    }
                }
        );

        result.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                Log.d(TAG, "setLocationListener() result status: " + status);
            }
        });
    }

    private void getNewPlaces() {
        //update introview
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mIntroView.setIsFindingPlaces();
            }
        });

        //find some places!
        new AsyncTask<Void, Void, List<Place>>() {
            @Override
            protected List<Place> doInBackground(Void... params) {
                List<Place> places = null;

                try {
                    //places = mPlacesApi.getNearbyPlaces(mLastLocation.getLatitude(), mLastLocation.getLongitude(), MAX_RADIUS, 60);
                } catch (Exception e) {
                    //if getNearbyPlaces fails, return null and directional will do what it needs to
                    Log.e(TAG, e.getLocalizedMessage());
                    e.printStackTrace();
                }
                return places;
            }

            @Override
            protected void onPostExecute(List<Place> places) {
//                if (places == null) {
//                    Toast.makeText(
//                            MainActivity.this,
//                            "There are no places near you - Please try again later.",
//                            Toast.LENGTH_LONG
//                    ).show();

                    //     goBackToSplash();
                    //     return;
                    places = new LinkedList<Place>();
                    Place tivoli = new Place("A", 51.505474, -0.024262);
                    places.add(tivoli);
//                }

                mHasPlaces = true;
                startTracking();

                mDirectionalTextViewContainer.updatePlaces(places, mLastLocation);

                showSwingPhoneView();
            }
        }.execute();
    }

    private void showSwingPhoneView() {
        mIntroView.animateOut();

        //animate in triggers its own animate out once completed, the next method
        mSwingPhoneView.animateIn();
    }

    public void onEvent(SwingPhoneView.OnAnimateOutCompleteEvent event) {
        mDirectionalTextViewContainer.animateIn();

        if (mOperasView.getVisibility() == View.GONE) {
            mOperasView.setVisibility(View.VISIBLE);
        }
    }

    private void goBackToSplash() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //unneccessary with finish()?
        startActivity(intent);

        this.finish();
    }

    private void startTracking() {
        mIsTracking = true;

        mTrackingHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mIsTracking) return;

                mHeadTracker.getLastHeadView(mHeadTransform.getHeadView(), 0);
                mHeadTransform.getEulerAngles(mEulerAngles, 0);

                runOnUiThread(updateDirectionalTextView);

                mTrackingHandler.postDelayed(this, 100);
            }
        });
    }

    private Runnable updateOperaImageView = new Runnable() {
        @Override
        public void run() {
            ArrayList<Pair<Integer, Float>> operaList = new ArrayList<>();
            List<Place> places = new ArrayList<>();

            Place place_a = new Place("Arms Open", 51.505474, -0.024262);
            Place place_b = new Place("Couple on Seat", 51.505445, -0.022827);
            Place place_c = new Place("Two Man", 51.504663, -0.023042);

            place_a.setImageResId(io.snapback.magicwhand.R.drawable.armsopen_r);
            place_b.setImageResId(io.snapback.magicwhand.R.drawable.coupleonseat_r);
            place_c.setImageResId(io.snapback.magicwhand.R.drawable.twoman_r);


            places.addAll(Arrays.asList(place_a, place_b, place_c));


            double heading = Math.toDegrees(mEulerAngles[1]);

            operaList = getOperaList(places, mLastLocation, heading);

            for (Pair p : operaList) {
                Log.d(TAG, "run: OperaList " + p.first.toString() + " " + p.second.toString());
            }

            mMainOperaIV.setImageDrawable( ContextCompat.getDrawable(context, places.get(operaList.get(0).first).getImageResId() ));
            mMainOperaTV.setText(context.getString(io.snapback.magicwhand.R.string.confidence, operaList.get(0).second.toString()));

            mSecondOperaIV.setImageDrawable( ContextCompat.getDrawable(context, places.get(operaList.get(1).first).getImageResId() ));
            mSecondOperaTV.setText(context.getString(io.snapback.magicwhand.R.string.confidence, operaList.get(1).second.toString()));

            mThirdOperaIV.setImageDrawable( ContextCompat.getDrawable(context, places.get(operaList.get(2).first).getImageResId() ));
            mThirdOperaTV.setText(context.getString(io.snapback.magicwhand.R.string.confidence, operaList.get(2).second.toString()));




        }
    };


    private ArrayList<Pair<Integer, Float>> getOperaList(List<Place> operalist, Location position, double heading) {
        ArrayList<Pair<Integer, Float>> operaList = new ArrayList<>();

        for (Place p : operalist) {
            int index = operalist.indexOf(p);
            float confidence = 6.0f * index + 18;
            Pair pair_element = new Pair(index, confidence);
            operaList.add(pair_element);
        }

        java.util.Collections.shuffle(operaList);
        return operaList;

    }


    private Runnable updateDirectionalTextView = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "run: Euler Angles " + mEulerAngles[0] + " " + mEulerAngles[1] + " " + mEulerAngles[2]);

            mDirectionalTextViewContainer.updateView(Math.toDegrees(mEulerAngles[1]));
        }
    };

    //google api stuffs

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected() " + (bundle != null ? bundle.toString() : "null"));

        mIsConnectedToGApi = true;

        if (mIsReadyToCheckLastLocation) {
            checkLastLocation();
            mIsReadyToCheckLastLocation = false;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended() " + i);
        mIsConnectedToGApi = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed() " + connectionResult);
        GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Log.d(TAG, "onCancelDialog()");
            }
        }).show();
    }


    static private int lastMsgWhat;

    // handle periodic message coming from thread
    private boolean handleMessage(Message msg) {

        int idleTimeMs = 250;
        // Debug only
        if (lastMsgWhat != msg.what) {
            Log.d(TAG, "State: from " + state2string(lastMsgWhat) + " to "
                    + state2string(msg.what));
        }
        lastMsgWhat = msg.what;

        Log.d(TAG, "State: " + state2string(msg.what));

        int orientation = inclinationDetector.getHeadingDeg();

        Log.d(TAG, "Heading " + orientation);

        switch (msg.what) {

            case MSG_STOP:
                Log.d(TAG, "handleMessage: STOP ");

                break;

            case MSG_START:
                Log.d(TAG, "handleMessage: START ");

                // continue, do not break

            case MSG_STATE_IDLE: {
                final int gestureTimeThrMs = 2000;
                int nextMsg = MSG_STATE_IDLE;
                int nextMsgTime = idleTimeMs;
                //  runOnUiThread(updateDirectionalImageView);


                // Recognise coupling gesture
                //trackedTarget = pointingTarget(orientation);


                if (gestureEngageDetection()) {
                    nextMsg = MSG_STATE_GESTURE_TRACK;
                    nextMsgTime = gestureTimeThrMs;
                    Log.d(TAG, "handleMessage: coupled and waiting 2 secs for PLAY/RESUME ");
                }


                sendMessage(nextMsg, nextMsgTime);
            }

            break;


            case MSG_STATE_GESTURE_TRACK: {
                int nextMsgTime = idleTimeMs;
                Log.d(TAG, "handleMessage: gesture track PLAY/RESUME ");
                runOnUiThread(updateOperaImageView);

                sendMessage(MSG_STATE_IDLE, nextMsgTime);
            }
            break;
            default:
                // should never happen
                break;
        }


        return true;
    }


    String state2string(int state) {
        String str;
        switch (state) {
            case MSG_STOP:
                str = "MSG_STOP";
                break;
            case MSG_START:
                str = "MSG_START";
                break;
            case MSG_STATE_IDLE:
                str = "MSG_STATE_IDLE";
                break;
            case MSG_STATE_COUPLING_TRACK:
                str = "MSG_STATE_COUPLING_TRACK";
                break;
            case MSG_STATE_GESTURE_TRACK:
                str = "MSG_STATE_GESTURE_TRACK";
                break;
            case MSG_STATE_GESTURE_PAUSE_STOP_TRACK:
                str = "MSG_STATE_GESTURE_PAUSE_STOP_TRACK";
                break;
            default:
                str = "??? (" + state + ")";
                break;
        }
        return str;
    }


    private boolean gestureEngageDetection() {
        // Add here conditions to recognise more gesture, e.g. GESTURE_BAR
        return inclinationDetector.isHorizontalFaceUp();
    }

    private void sendMessage(int what) {
        sendMessage(what, 0);
    }

    private void sendMessage(int what, long delayMillis) {
        Message msg = motionHandler.obtainMessage(what);
        if (delayMillis > 0) {
            motionHandler.sendMessageDelayed(msg, delayMillis);
        } else {
            motionHandler.sendMessage(msg);
        }
    }


}
