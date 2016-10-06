package de.max.miband.plugins;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.telecom.Call;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Set;

import de.max.miband.ActionCallback;
import de.max.miband.DateUtils;
import de.max.miband.DeviceInfo;
import de.max.miband.MiBand;
import de.max.miband.NotifyListener;
import de.max.miband.RealtimeStepsNotifyListener;
import de.max.miband.model.BatteryInfo;
import de.max.miband.models.ActivityData;
import de.max.miband.sqlite.ActivitySQLite;

/**
 * Created by Max on 26.06.2016.
 */
public class MiBandPlugin extends CordovaPlugin {
    private Context applicationContext;
    private String TAG ="MiBandPlugin";

    private long getStartOfDayInMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDayInMillis() {
        // Add one day's time to the beginning of the day.
        // 24 hours * 60 minutes * 60 seconds * 1000 milliseconds = 1 day
        return getStartOfDayInMillis() + (24 * 60 * 60 * 1000);
    }

    private int readActivityData(){
        Calendar before = Calendar.getInstance();
        //7 days before
        before.add(Calendar.DAY_OF_WEEK, -7);

        int start= (int) (getStartOfDayInMillis()/1000);
        int end= (int) (System.currentTimeMillis() / 1000);

        //use DateUtils to display the time in the format "yyyy-MM-dd HH:mm:ss"
        Log.i(TAG, "data from " + start + " to " + end);

        //all our data is stored in ActivitySQLite as ActivityData objects
        ArrayList<ActivityData> allActivities = ActivitySQLite.getInstance(this.cordova.getActivity()).getAllActivitiesSamples(start, end);

        float movement_divisor = 180.0f;
        float value;
        int totalSteps=0;

        String dateString = "";
        String firstDate = "";
        boolean isFirst=true;
        for (ActivityData ad : allActivities) {

            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(ad.getTimestamp() * 1000L);

            dateString = DateUtils.convertString(date);
            if (isFirst){
                firstDate=dateString;
                isFirst=false;
            }
            Log.i(TAG, "date " + dateString);
            Log.i(TAG, "steps " + ad.getSteps());
            totalSteps+=ad.getSteps();

            value = ((float) ad.getIntensity()) / movement_divisor;
            //Log.i(TAG, "value "+value);
        }

        Log.d(TAG, "FIRST DATE:::::::"+firstDate);
        Log.d(TAG, "LAST DATE:::::::"+dateString);
        Log.d(TAG, "TOTAL STEPS FOR TODAY:::::::"+totalSteps);
        return totalSteps;
    }


    private void synchronizeMiBand(final MiBand miBand, final CallbackContext callbackContext){
        miBand.startListeningSync(new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                if (data != null && data.equals("sync complete")) {
                    Log.d(TAG, "Synchronization successfully completed!");

                    int synchSteps = readActivityData();
                    sendResult(callbackContext, Integer.toString(synchSteps), true);
                }
            }

            @Override
            public void onFail(int errorCode, String msg) {
                Log.d(TAG, "Synchronization Failed!: " + msg);
                sendResult(callbackContext, "Synchronization Failed: " + msg, false);
            }
        });
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.cordova.CordovaPlugin#execute(java.lang.String,
     * org.json.JSONArray, org.apache.cordova.CallbackContext)
     */

    private void sendResult(final CallbackContext callbackContext, String msg, boolean success){
        JSONObject result = null;
        try {
            result = new JSONObject();
            result.put("msg", msg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult pluginresult;
        if (success){
            pluginresult=  new PluginResult(PluginResult.Status.OK, result);
        }else{
            pluginresult = new PluginResult(PluginResult.Status.ERROR, result);
        }

        pluginresult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginresult);
    }


    @Override
    public boolean execute(String action, JSONArray data,
                           final CallbackContext callbackContext) throws JSONException {
        // get application Context
        applicationContext = this.cordova.getActivity();
        //Search for a Band
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        boolean foundBand=false;
        String address="";
        for(BluetoothDevice device : pairedDevices){
            Log.d(TAG, "Found: "+device.getName()+ " | "+device.getAddress());
            if (device.getName() != null && device.getAddress() != null && device.getName().startsWith("MI")){ //&& device.getAddress().startsWith("88:0F:10")) {
                address=device.getAddress();
                foundBand=true;
            }
        }
        if (!foundBand){
            sendResult(callbackContext, "No MiBand found to pair with.",false);
            return true;
        }

        //Get MiBand
        final MiBand miBand = MiBand.getInstance(applicationContext,address);

        //Connect to MiBand
        if (action.equals("connectBand")){
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "CONNECT BAND CALLED");
                    if (!miBand.isConnected() && !miBand.isConnecting()) {
                        miBand.connect(new ActionCallback() {
                            @Override
                            public void onSuccess(Object data) {
                                Log.d(TAG, "Connected with Mi Band!");

                                //Set Notify Listeners
                                miBand.setSensorDataNotifyListener(new NotifyListener() {
                                    @Override
                                    public void onNotify(byte[] data) {
                                        int counter=0, step=0, axis1=0, axis2=0, axis3 =0;
                                        if((data.length - 2) % 6 != 0) {
                                            Log.e(TAG,"GOT UNEXPECTED SENSOR DATA WITH LENGTH: " + data.length);
                                            for (byte b : data) {
                                                Log.e(TAG,"DATA: " + String.format("0x%4x", b));
                                            }
                                        } else {
                                            counter = (data[0] & 0xff) | ((data[1] & 0xff) << 8);
                                            for (int idx = 0; idx < ((data.length - 2) / 6); idx++) {
                                                step = idx * 6;
                                                axis1 = (data[step+2] & 0xff) | ((data[step+3] & 0xff) << 8);
                                                axis2 = (data[step+4] & 0xff) | ((data[step+5] & 0xff) << 8);
                                                axis3 = (data[step+6] & 0xff) | ((data[step+7] & 0xff) << 8);
                                            }

                                            String msg="a1:"+Integer.toString(axis1)+" | a2:"+Integer.toString(axis2)+" | a3:"+Integer.toString(axis3)+";";
                                            Log.d(TAG,msg);
                                            sendResult(callbackContext, msg, true);
                                        }
                                    }
                                });

                                miBand.setRealtimeStepsNotifyListener(new RealtimeStepsNotifyListener() {
                                    @Override
                                    public void onNotify(int steps) {
                                        sendResult(callbackContext, Integer.toString(steps), true);
                                    }
                                });


                                sendResult(callbackContext, "Connected to " + miBand.getAddress(), true);

                            }

                            @Override
                            public void onFail(int errorCode, String msg) {
                                Log.d(TAG, "Connection failed: " + msg);
                                sendResult(callbackContext, "Disconnected from "+miBand.getAddress(), false);
                            }
                        });
                    }
                    else {
                        if (miBand.isConnected()){
                            miBand.disconnect();
                            sendResult(callbackContext, "Disconnected from " + miBand.getAddress(), true);
                        }

                    }
                }
            });
            return true;
        }

        if (action.equals("enableSensorDataNotify")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "GET LIVE SENSOR CALLED");

                    sendResult(callbackContext, "Enable Realtime Sensor succeeded", true);

                    miBand.enableSensorDataNotify(new ActionCallback() {
                        @Override
                        public void onSuccess(Object data) {
                            sendResult(callbackContext, "Enable Realtime Sensor succeeded", true);
                        }

                        @Override
                        public void onFail(int errorCode, String msg) {
                            sendResult(callbackContext, "Enable Realtime Sensor failed", false);
                        }
                    });

                }
            });

            return true;
        }

        if (action.equals("disableSensorDataNotify")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "DISABLE LIVE SENSOR CALLED");
                    miBand.disableSensorDataNotify(new ActionCallback() {
                        @Override
                        public void onSuccess(Object data) {
                            sendResult(callbackContext, "Disable Realtime Sensor succeeded", true);
                        }

                        @Override
                        public void onFail(int errorCode, String msg) {
                            sendResult(callbackContext, "Disable Realtime Sensor failed", false);
                        }
                    });
                }
            });
            return true;
        }

        if (action.equals("enableLiveStepsNotify")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "GET LIVE STEPS CALLED");
                    miBand.enableRealtimeStepsNotify(new ActionCallback() {
                        @Override
                        public void onSuccess(Object data) {
                            sendResult(callbackContext, "Enable Realtime Steps succeeded", true);
                        }

                        @Override
                        public void onFail(int errorCode, String msg) {
                            sendResult(callbackContext, "Enable Realtime Steps failed", false);
                        }
                    });
                }
            });
            return true;
        }

        if (action.equals("disableLiveStepsNotify")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "DISABLE LIVE STEPS CALLED");
                    miBand.disableRealtimeStepsNotify(new ActionCallback() {
                        @Override
                        public void onSuccess(Object data) {
                            sendResult(callbackContext, "Disable Realtime Steps succeeded", true);
                        }

                        @Override
                        public void onFail(int errorCode, String msg) {
                            sendResult(callbackContext, "Disable Realtime Steps failed", false);
                        }
                    });
                }
            });
            return true;
        }

        if (action.equals("getLiveStepCount")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "GET LIVE STEPCOUNT CALLED");
                    if (miBand.isConnected()) {
                        miBand.readCurrentStepCount(new ActionCallback() {
                            @Override
                            public void onSuccess(Object data) {
                                int steps = (int) data;
                                sendResult(callbackContext, Integer.toString(steps), true);
                            }

                            @Override
                            public void onFail(int errorCode, String msg) {
                                sendResult(callbackContext, "Read live step count failed", false);
                            }
                        });
                    } else {
                        sendResult(callbackContext, "Mi Band is not connected", false);
                    }
                }
            });
            return true;
        }

        if (action.equals("getBatteryInfo")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "GET BATTERY INFO CALLED");
                    if (miBand.isConnected()) {
                        miBand.getBatteryInfo(new ActionCallback() {
                            @Override
                            public void onSuccess(final Object data) {
                                BatteryInfo battery = (BatteryInfo) data;
                                //get the cycle count, the level and other information
                                sendResult(callbackContext, battery.toString(), true);
                            }

                            @Override
                            public void onFail(int errorCode, String msg) {
                                sendResult(callbackContext, msg, false);
                            }
                        });

                        sendResult(callbackContext, "", false);
                    } else {
                        sendResult(callbackContext, "Mi Band is not connected", false);
                    }
                }
            });
            return true;
        }

        // Synchronize MiBand
        if (action.equals("synchronizeBand")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.d(TAG, "SYNCHRONIZE BAND CALLED");
                    if (miBand.isConnected()) {
                        synchronizeMiBand(miBand,callbackContext);
                    } else {
                        sendResult(callbackContext, "Mi Band is not connected", false);
                    }
                }
            });
            return true;
        }

        return false;
    }
}
