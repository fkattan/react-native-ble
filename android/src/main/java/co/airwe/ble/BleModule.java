package co.airwe.ble;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;


public class BleModule extends ReactContextBaseJavaModule implements LifecycleEventListener, BeaconConsumer, MonitorNotifier, RangeNotifier {

    private final String TAG = "BleModule";

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private static final boolean IS_DEBUG = true;

    private static final int BLE_SCAN_PERIOD = 1250; // 1.1s
    private static final int BLE_SCAN_FREQUENCY = 60000; // 1min
    private static final String BLE_REGION_ID = "co.airwe.covid";
    public static final String EDDYSTONE_NAMESPACE_ID = "0xec870c5657e46bff08a5";
    public static final String IBEACON_UUID = "c5f4271d-1141-462f-9b31-4ba0a39ef7fb";


    private static final String BLE_START_RANGING_ERROR_EVENT = "co.airwe.ble.startRangingBeaconsError";
    private static final String BLE_STOP_RANGING_ERROR_EVENT = "co.airwe.ble.stopRangingBeaconsError";
    private static final String BLE_START_MONITORING_ERROR_EVENT = "co.airwe.ble.startMonitoringBeaconsError";
    private static final String BLE_STOP_MONITORING_ERROR_EVENT = "co.airwe.ble.stopMonitoringBeaconsError";

    private final ReactApplicationContext reactContext;
    private BeaconManager mBeaconManager;
    private boolean isBleReady = false;

    public BleModule(ReactApplicationContext reactContext) {
      super(reactContext);
      this.reactContext = reactContext;
      reactContext.addLifecycleEventListener(this);

      BeaconManager.setDebug(IS_DEBUG);
      mBeaconManager = BeaconManager.getInstanceForApplication(reactContext.getApplicationContext());
    }

    @Override
    public String getName() {
        return "Ble";
    }

    @ReactMethod
    public void startMonitoringRegion() {

      try {
        requireBleReady();

        mBeaconManager.addMonitorNotifier(this);
        mBeaconManager.startMonitoringBeaconsInRegion(getDefaultRegion());

      } catch (RemoteException e) {
        Log.e(TAG, "Error trying to monitor Region: " + BLE_REGION_ID);
        e.printStackTrace();

        WritableMap params = Arguments.createMap();
        params.putString("message", e.getMessage());
        params.putString("stackTrace", e.getStackTrace().toString());
        params.putString("type", "RemoteException");
        sendEvent(reactContext, BLE_START_MONITORING_ERROR_EVENT, params);

      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
        return;
      }
    }

    @ReactMethod
    public void stopMonitoringRegion() {
      try {
        requireBleReady();
        mBeaconManager.stopMonitoringBeaconsInRegion(getDefaultRegion());

      } catch (RemoteException e) {
        Log.e(TAG, "Error trying to stop monitoring Region: " + BLE_REGION_ID);
        e.printStackTrace();

        WritableMap params = Arguments.createMap();
        params.putString("message", e.getMessage());
        params.putString("stackTrace", e.getStackTrace().toString());
        params.putString("type", "RemoteException");
        sendEvent(reactContext, BLE_STOP_MONITORING_ERROR_EVENT, params);

      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
        return;
      }
    }

    @ReactMethod
    public void startRangingRegion() {
      try {
        requireBleReady();
        mBeaconManager.addRangeNotifier(this);
        mBeaconManager.startRangingBeaconsInRegion(getDefaultRegion());
      } catch(RemoteException e) {
        Log.e(TAG, "Error trying to start ranging beacons in region: " + BLE_REGION_ID);
        e.printStackTrace();

        WritableMap params = Arguments.createMap();
        params.putString("message", e.getMessage());
        params.putString("stackTrace", e.getStackTrace().toString());
        params.putString("type", "RemoteException");
        sendEvent(reactContext, BLE_START_RANGING_ERROR_EVENT, params);

      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
        return;
      }
    }

    @ReactMethod
    public void stopRangingRegion() {
      try {
        requireBleReady();
        mBeaconManager.stopRangingBeaconsInRegion(getDefaultRegion());

      } catch(RemoteException e) {
        Log.e(TAG, "Error trying to stop ranging beacons in region: " + BLE_REGION_ID);
        e.printStackTrace();

        WritableMap params = Arguments.createMap();
        params.putString("message", e.getMessage());
        params.putString("stackTrace", e.getStackTrace().toString());
        params.putString("type", "RemoteException");
        sendEvent(reactContext, BLE_STOP_RANGING_ERROR_EVENT, params);

      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
        return;
      }
    }

    @SuppressLint("NewApi")
    @ReactMethod
    public void initializeBeaconManager() {
      Context context = reactContext;

      BeaconManager.setDebug(true);

      // new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT)
      mBeaconManager = BeaconManager.getInstanceForApplication(reactContext.getApplicationContext());

      // iBeacon Parser
      mBeaconManager.getBeaconParsers().add(
        new BeaconParser().setBeaconLayout(
          "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        )
      );

      Class mainActivityClass = null;
      try {
        mainActivityClass = getMainActivityClassName(context);

      } catch (ClassNotFoundException e) {
        Log.e(TAG, "Can't get Class for MainActivity");
        e.printStackTrace();
        return;
      }

      //Notification.Builder builder = new Notification.Builder(context);
      Notification.Builder builder = new Notification.Builder(reactContext, "co.airwe.scanNotification");

      builder.setSmallIcon(R.mipmap.ic_launcher);
      builder.setContentTitle("Scanning for Beacons");

      Intent intent = new Intent(this.getCurrentActivity(), mainActivityClass);

      PendingIntent pendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
      );

      builder.setContentIntent(pendingIntent);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel = new NotificationChannel(
          "co.airwe.frac.ble",
          "BleNotification",
          NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("My Notification Channel Description");
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(
          Context.NOTIFICATION_SERVICE
        );
        notificationManager.createNotificationChannel(channel);
        Log.d(TAG, "Notification Channel ID: " + channel.getId());
        builder.setChannelId(channel.getId());
      }

      Log.d(TAG, "Enabling Foreground Service");
      mBeaconManager.enableForegroundServiceScanning(builder.build(), 456);

      // For the above foreground scanning service to be useful, you need to disable
      // JobScheduler-based scans (used on Android 8+) and set a fast background scan
      // cycle that would otherwise be disallowed by the operating system.
      //
      mBeaconManager.setEnableScheduledScanJobs(false);

      mBeaconManager.setForegroundBetweenScanPeriod(BLE_SCAN_PERIOD);
      mBeaconManager.setForegroundBetweenScanPeriod(BLE_SCAN_FREQUENCY);

      mBeaconManager.bind(this);
    }


    // LifecycleEventListener

    @Override
    public void onHostResume() {
      mBeaconManager = BeaconManager.getInstanceForApplication(reactContext.getApplicationContext());
    }

    @Override
    public void onHostPause() {
    }

    @Override
    public void onHostDestroy() {
    }


    // BeaconConsumer

    @Override
    public void onBeaconServiceConnect() {
      Log.d(TAG, "BeaconManager initialized !!!!");
      isBleReady = true;
    }

    @Override
    public Context getApplicationContext() {
      Log.d(TAG, "Returning ApplicationContext for Beacon Consumer");
      return reactContext.getApplicationContext();
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
      Log.d(TAG, "UnBinding BeaconConsumer");
      reactContext.getApplicationContext().unbindService(serviceConnection);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
      Log.d(TAG, "Binding BeaconConsumer");
      return reactContext.getApplicationContext().bindService(intent, serviceConnection, i);
    }

    // MonitorNotifier

    @Override
    public void didEnterRegion(Region region) {
        Log.d(TAG, "DidEnterRegion: " + region.getUniqueId());
    }

    @Override
    public void didExitRegion(Region region) {
      Log.d(TAG, "DidExitRegion: " + region.getUniqueId());
    }

    @Override
    public void didDetermineStateForRegion(int i, Region region) {
        Log.d(TAG, "DidDetermineStateForRegion: " +
          (i == MonitorNotifier.INSIDE ? "INSIDE" : "OUTSIDE") +
          " Region: " + region.getUniqueId());
    }

    // RangeNotifier

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {

    }

    // Private Methods

    private Region getDefaultRegion() {

      //Identifier namespaceIdentifier = Identifier.parse(EDDYSTONE_NAMESPACE_ID);
      Identifier namespaceIdentifier = Identifier.parse(IBEACON_UUID);
      Region region = new Region(BLE_REGION_ID, namespaceIdentifier, null, null);

      return region;
    }

  private void requireBleReady() throws Exception {

      if(!isBleReady) {

        final String message = "Invalid call to beacon ranging or monitoring; BLE not ready. Make sure to call initializeBeaconManager before making this call";

        Log.e(TAG, message);

        WritableMap params = Arguments.createMap();
        params.putString("message", message);
        sendEvent(reactContext, BLE_START_MONITORING_ERROR_EVENT, params);

        throw new Exception(message);
      }

      return;
    }

    private void sendEvent(ReactContext context, String eventName, @Nullable WritableMap params) {
      context
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
    }

    private Class getMainActivityClassName(Context context) throws ClassNotFoundException {
      Log.d(TAG, "in getMainActivityClassName, Context: " + context);

      String packageName = context.getPackageName();
      Log.d(TAG, "Package Name: " + packageName);
      Intent launchIntent = reactContext.getPackageManager().getLaunchIntentForPackage(packageName);

      if(launchIntent == null || launchIntent.getComponent() == null) {
        Log.e(TAG, "Failed to get Intent for Package Name: " + packageName);
        return null;
      }

      Class<?> mainActivityClass = Class.forName(launchIntent.getComponent().getClassName());
      Log.d(TAG, "MainActivity Class:  " + mainActivityClass);

      return mainActivityClass;
    }

}
