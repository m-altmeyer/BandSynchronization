package de.max.miband;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.max.miband.bluetooth.BLEAction;
import de.max.miband.bluetooth.BLETask;
import de.max.miband.bluetooth.BTCommandManager;
import de.max.miband.bluetooth.BTConnectionManager;
import de.max.miband.bluetooth.MiBandDateConverter;
import de.max.miband.bluetooth.MiBandWrapper;
import de.max.miband.bluetooth.WaitAction;
import de.max.miband.bluetooth.WriteAction;
import de.max.miband.model.BatteryInfo;
import de.max.miband.model.LedColor;
import de.max.miband.model.Profile;
import de.max.miband.model.Protocol;
import de.max.miband.model.UserInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class MiBand {
    private static final String TAG = "MiBand";
    private static Context context;
    private static String address;
    private static BTCommandManager io;
    private static MiBand instance;
    private static MiBandWrapper miBandWrapper;
    private static Intent miBandService;
    private static BTConnectionManager btConnectionManager;
    private boolean currentlySynching = false;
    private ActionCallback connectionCallback;
    private ActionCallback currentSynchCallback;
    private DeviceInfo mDeviceInfo;

    public MiBand(final Context context, final String address) {
        MiBand.context = context;
        MiBand.address = address;
        MiBand.miBandWrapper = MiBandWrapper.getInstance(context);

        ActionCallback myConnectionCallback = new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                Log.d(TAG, "Connection success, now pair: " + data);

                //only once we are paired, we create the BluetoothIO object to communicate with Mi Band
                io = new BTCommandManager(context, btConnectionManager.getGatt());
                btConnectionManager.setIo(io);
                //Clear Queue
                io.clearQueue();
                //setLowLatency();
                btConnectionManager.enableNotifications(true);

                readDeviceInfo(new ActionCallback() {
                    @Override
                    public void onSuccess(Object data) {
                        BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) data;
                        byte[] value = characteristic.getValue();
                        mDeviceInfo = new DeviceInfo(value);
                        Log.d(TAG, "Device info: " + mDeviceInfo);
                        setUserInfo(UserInfo.getDefault(getAddress(), mDeviceInfo), new ActionCallback() {
                            @Override
                            public void onSuccess(Object data) {
                                //Set to high Latency Mode
                                setHighLatency();
                                //Set Current Time
                                setCurrentTime(new ActionCallback() {
                                    @Override
                                    public void onSuccess(Object data) {
                                        Log.d(TAG, "Current Date successfully set.");
                                        readDate(new ActionCallback() {
                                            @Override
                                            public void onSuccess(Object data) {
                                                GregorianCalendar calendar = MiBandDateConverter.rawBytesToCalendar((byte[]) data);
                                                Log.d(TAG, "Current Date on MiBand successfully read: " + DateUtils.convertString(calendar));
                                                //Set Step Goal
                                                setFitnessGoal(99999, new ActionCallback() {
                                                    @Override
                                                    public void onSuccess(Object data) {
                                                        Log.d(TAG, "Set Fitness Goal successfully");
                                                    }

                                                    @Override
                                                    public void onFail(int errorCode, String msg) {
                                                        Log.e(TAG, "Set Fitness Goal failed");
                                                        disconnect();
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onFail(int errorCode, String msg) {
                                                Log.e(TAG, "Error reading Date: " + msg);
                                                disconnect();
                                            }
                                        });
                                    }

                                    @Override
                                    public void onFail(int errorCode, String msg) {
                                        Log.e(TAG, "Error setting Date: " + msg);
                                        disconnect();
                                    }
                                });
                            }

                            @Override
                            public void onFail(int errorCode, String msg) {
                                Log.e(TAG, "Error setting UserInfo: " + msg);
                                disconnect();
                            }
                        });
                    }

                    @Override
                    public void onFail(int errorCode, String msg) {
                        Log.e(TAG, "No device info");
                        disconnect();
                    }
                });

                if (connectionCallback != null)
                    connectionCallback.onSuccess(null);
            }


            @Override
            public void onFail(int errorCode, String msg) {
                Log.e(TAG, "Fail: " + msg);
                if (connectionCallback != null)
                    connectionCallback.onFail(errorCode, msg);
            }
        };

        MiBand.btConnectionManager = BTConnectionManager.getInstance(context, myConnectionCallback);
        //Enable Notifications
        btConnectionManager.toggleNotifications(true);
    }


    public synchronized static MiBand getInstance(Context context, String imei) {
        if (instance == null) {
            instance = new MiBand(context, imei);
        } else {
            MiBand.context = context;
        }
        return instance;
    }

    /**
     * Can be used to check wether the sync process is running
     * @return true, if currently synching steps
     */
    public boolean isCurrentlySynching() {
        return currentlySynching;
    }

    /**
     * Sets a fitness goal on the Band
     * @param fitnessGoal, the goal to be set
     * @param callback, the Action Callback to be called
     */
    public void setFitnessGoal(int fitnessGoal, final ActionCallback callback) {
        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, new byte[]{
                Protocol.COMMAND_SET_FITNESS_GOAL,
                0,
                (byte) (fitnessGoal & 0xff),
                (byte) ((fitnessGoal >>> 8) & 0xff)
        }, callback));
        queue(list);
    }

    /**
     * Sets the Date on the Band
     * @param callback, the Action Callback
     */
    private void setCurrentTime(ActionCallback callback) {
        Calendar now = GregorianCalendar.getInstance();
        Date date = now.getTime();
        Log.d(TAG, "Sending current time to Mi Band: " + date + " (" + date.toGMTString() + ")");
        byte[] nowBytes = MiBandDateConverter.calendarToRawBytes(now);
        byte[] time = new byte[]{
                nowBytes[0],
                nowBytes[1],
                nowBytes[2],
                nowBytes[3],
                nowBytes[4],
                nowBytes[5],
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f,
                (byte) 0x0f
        };

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_DATA_TIME, time, callback));
        queue(list);
        Log.d(TAG, "Date set.");
    }

    /**
     * Reads the current date on the MiBand
     * !CALL 'setDate' FIRST!
     * @param callback, the Action Callback to be called
     */
    public void readDate(final ActionCallback callback) {
        checkConnection();
        ActionCallback ioCallback = new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) data;
                Log.d(TAG, "getDate result " + Arrays.toString(characteristic.getValue()));
                callback.onSuccess(characteristic.getValue());
            }

            @Override
            public void onFail(int errorCode, String msg) {
                callback.onFail(errorCode, msg);
            }
        };
        MiBand.io.readCharacteristic(Profile.UUID_CHAR_DATA_TIME, ioCallback);
    }

    /**
     * Read the current Step Count (Without synching data!)
     * @param callback, the Action Callback to be called (on Success, steps are returned as int)
     */
    public void readCurrentStepCount(final ActionCallback callback) {
        checkConnection();
        ActionCallback ioCallback = new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) data;
                byte[] value = characteristic.getValue();
                int steps = 0xff & value[0] | (0xff & value[1]) << 8;
                Log.d(TAG, "getCurrentStepCount result " + steps);
                callback.onSuccess(steps);
            }

            @Override
            public void onFail(int errorCode, String msg) {
                callback.onFail(errorCode, msg);
            }
        };

        MiBand.io.readCharacteristic(Profile.UUID_CHAR_REALTIME_STEPS, ioCallback);
    }

    /**
     * Initiate the synchronization process
     * @param actionCallback, the Action Callback to be called
     * Data remains always on the Band to not lose anything
     * Synchronized data directly is stored in the internal sqlite db
     */
    public void startListeningSync(final ActionCallback actionCallback) {
        checkConnection();
        btConnectionManager.enableSynchronization(true);
        this.io.setSynchFail(false);
        currentlySynching = true;
        Log.d(TAG, "Synching running....");
        currentSynchCallback = actionCallback;

        final List<BLEAction> list = new ArrayList<>();

        list.add(new WriteAction(Profile.UUID_CHAR_LE_PARAMS, this.io.getLowLatency(), new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                Log.d(TAG, "Set MiBand to Low Latency Mode");
                currentSynchCallback = actionCallback;

                final List<BLEAction> list2 = new ArrayList<>();
                list2.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.FETCH_DATA, new ActionCallback() {
                    @Override
                    public void onSuccess(Object data) {
                        actionCallback.onSuccess(data);

                        io.setCurrentSynchCallback(new ActionCallback() {
                            @Override
                            public void onSuccess(Object data) {
                                currentlySynching = false;
                                Log.d(TAG, "Synching stopped.");
                                currentSynchCallback.onSuccess(data);
                            }

                            @Override
                            public void onFail(int errorCode, String msg) {
                                currentlySynching = false;
                                currentSynchCallback.onFail(errorCode, msg);
                                Log.d(TAG, "Synching stopped (ERR).");
                            }
                        });
                    }

                    @Override
                    public void onFail(int errorCode, String msg) {
                        currentlySynching = false;
                        actionCallback.onFail(errorCode, msg);
                        Log.d(TAG, "Synching stopped (ERR).");
                    }
                }));
                queue(list2);
            }

            @Override
            public void onFail(int errorCode, String msg) {
                Log.d(TAG, "Setting to Low Latency Mode Failed");
                actionCallback.onFail(333, "LOW LATENCY FAIL");
            }
        }));

        queue(list);
    }

    /**
     * Sets the Band to high latency mode
     * Should be the default state of the communication
     */
    public void setHighLatency() {
        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_LE_PARAMS, io.getHighLatency()));
        queue(list);
        Log.d(TAG, "Setting High Latency Mode");
    }

    /**
     * Sets the Band to low latency mode
     * Can be used to have a more reliable connection (e.g.when synching)
     */
    public void setLowLatency() {
        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_LE_PARAMS, io.getLowLatency()));
        queue(list);
        Log.d(TAG, "Setting Low Latency Mode");
    }


    /**
     * Disconnect from the band
     */
    public static void disconnect() {
        Log.e(TAG, "Disconnecting Mi Band...");
        if (miBandService != null)
            MiBand.context.stopService(miBandService);
        btConnectionManager.disconnect();
    }

    /**
     * Subscribe to sensor changes
     * !REMEMBER to turn on sensor notifications!
     * @param listener, a listener to be called
     */
    public void setSensorDataNotifyListener(final NotifyListener listener) {
        this.io.setNotifyListener(Profile.UUID_CHAR_SENSOR_DATA, new NotifyListener() {
            @Override
            public void onNotify(byte[] data) {
                listener.onNotify(data);
            }
        });
    }

    /**
     * Turn on Sensor Notifications
     * @param callback
     */
    public void enableSensorDataNotify(ActionCallback callback) {
        checkConnection();
        btConnectionManager.enableRealtimeNotifications(true);
        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.ENABLE_SENSOR_DATA_NOTIFY, callback));
        queue(list);
    }

    /**
     * Turn off Sensor Notifications
     * @param callback
     */
    public void disableSensorDataNotify(ActionCallback callback) {
        checkConnection();
        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.DISABLE_SENSOR_DATA_NOTIFY, callback));
        queue(list);
    }

    /**
     * Subscribe to step counter changes
     * !REMEMBER to turn on step notifications!
     * @param listener
     */
    public void setRealtimeStepsNotifyListener(final RealtimeStepsNotifyListener listener) {
        checkConnection();

        MiBand.io.setNotifyListener(Profile.UUID_CHAR_REALTIME_STEPS, new NotifyListener() {
            @Override
            public void onNotify(byte[] data) {
                Log.d(TAG, Arrays.toString(data));
                int steps = 0xff & data[0] | (0xff & data[1]) << 8;
                listener.onNotify(steps);
            }
        });
    }

    /**
     * Turn on Step Notifications
     * @param callback
     */
    public void enableRealtimeStepsNotify(ActionCallback callback) {
        checkConnection();
        btConnectionManager.enableRealtimeNotifications(true);

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.ENABLE_REALTIME_STEPS_NOTIFY, callback));

        queue(list);
    }

    /**
     * Turn off Step Notifications
     * @param callback
     */
    public void disableRealtimeStepsNotify(ActionCallback callback) {
        checkConnection();

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.DISABLE_REALTIME_STEPS_NOTIFY, callback));

        queue(list);
    }

    /**
     * Dispose the band
     */
    public static void dispose() {
        Log.e(TAG, "Disposing Mi Band...");
        if (miBandService != null)
            MiBand.context.stopService(miBandService);
        btConnectionManager.dispose();
    }

    /**
     * Connects to the (first) paired band in bonded devices
     *
     * @param callback
     */
    public void connect(final ActionCallback callback) {
        if (!isConnected()) {
            connectionCallback = callback;
            btConnectionManager.connect(address);
        } else {
            Log.e(TAG, "Already connected...");
        }
    }

    /**
     * Get the address of the connected band
     * @return the adress
     */
    public String getAddress() {
        if (!isConnected()) {
            return "";
        } else {
            return btConnectionManager.getDevice().getAddress();
        }
    }

    /**
     * Check if the Band is currently connected
     * @return true, if connected
     */
    private void checkConnection() {
        if (!isConnected()) {
            Log.e(TAG, "Not connected... Waiting for new connection...");
            btConnectionManager.connect(address);
        }
    }

    /**
     * Checks if the connection is already done with the Mi Band
     *
     * @return if the Mi Band is connected
     */
    public boolean isConnected() {
        return btConnectionManager.isConnected();
    }

    public boolean isConnecting() {
        return btConnectionManager.isConnecting();
    }

    /**
     * Read the device information
     * @param callback
     */
    public void readDeviceInfo(final ActionCallback callback) {
        ActionCallback cb = new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                MiBand.io.readCharacteristic(Profile.UUID_CHAR_DEVICE_NAME_2, callback);
            }

            @Override
            public void onFail(int errorCode, String msg) {
                callback.onFail(333, "Could not get Device Info");
            }
        };
        MiBand.io.readCharacteristic(Profile.UUID_CHAR_DEVICE_INFO, callback);
    }

    /**
     * Signal strength reading and the connected device RSSI value
     *
     * @param callback
     */
    public void readRssi(ActionCallback callback) {
        checkConnection();
        MiBand.io.readRssi(callback);
    }

    /**
     * Read band battery information
     * @param callback , the action callback containing the information
     */
    public void getBatteryInfo(final ActionCallback callback) {
        checkConnection();

        ActionCallback ioCallback = new ActionCallback() {
            @Override
            public void onSuccess(Object data) {
                BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) data;
                Log.d(TAG, "getBatteryInfo result " + Arrays.toString(characteristic.getValue()));
                if (characteristic.getValue().length == 10) {
                    BatteryInfo info = BatteryInfo.fromByteData(characteristic.getValue());
                    callback.onSuccess(info);
                } else {
                    callback.onFail(-1, "result format wrong!");
                }
            }

            @Override
            public void onFail(int errorCode, String msg) {
                callback.onFail(errorCode, msg);
            }
        };

        MiBand.io.readCharacteristic(Profile.UUID_CHAR_BATTERY, ioCallback);
    }


    /**
     * Vibrate "times" times. Each iteration will start vibrator "on_time" milliseconds (up to 500, will be truncated if larger), and then stop it "off_time" milliseconds (no limit here).
     *
     * @param times   : the amount of times to vibrate
     * @param onTime  : the time in milliseconds that each vibration will last (maximum of 500 milliseconds). Preferably more than 100 milliseconds
     * @param offTime : the time in milliseconds that each cycle will last
     */
    public synchronized void customVibration(final int times, final int onTime, final int offTime) {
        final int newOnTime = Math.min(onTime, 500);

        List<BLEAction> list = new ArrayList<>();

        for (int i = 1; i <= times; i++) {

            list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.VIBRATION_UNTIL_CALL_STOP));
            list.add(new WaitAction(newOnTime));
            list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.STOP_VIBRATION));
            list.add(new WaitAction(offTime));
        }

        queue(list);
    }


    /**
     * Sets the led light color. Flashes the LED's by default
     *
     * @param color : the given {@link LedColor} color
     */
    public void setLedColor(LedColor color) {
        setLedColor(color, true);
    }

    /**
     * Sets the led light color.
     *
     * @param color      : the given {@link LedColor} color
     * @param quickFlash : <b>true</b> if you want the band's LED's to flash, <b>false</b> otherwise
     */
    public void setLedColor(LedColor color, boolean quickFlash) {
        byte[] protocal;
        switch (color) {
            case RED:
                protocal = Protocol.COLOR_RED;
                break;
            case BLUE:
                protocal = Protocol.COLOR_BLUE;
                break;
            case GREEN:
                protocal = Protocol.COLOR_GREEN;
                break;
            case ORANGE:
                protocal = Protocol.COLOR_ORANGE;
                break;
            case TEST:
                protocal = Protocol.COLOR_TEST;
                break;
            default:
                return;
        }

        protocal[protocal.length - 1] = quickFlash ? (byte) 1 : (byte) 0;

        setColor(protocal);
    }

    /**
     * Sets the LED color. Flashes the LED's by default
     *
     * @param rgb : an <b>int</b> that represents the rgb value
     */
    public void setLedColor(int rgb) {
        setLedColor(rgb, true);
    }

    /**
     * Sets the LED color.
     *
     * @param rgb        : an <b>int</b> that represents the rgb value
     * @param quickFlash : <b>true</b> if you want the band's LED's to flash, <b>false</b> otherwise
     */
    public void setLedColor(int rgb, boolean quickFlash) {
        byte[] colors = convertRgb(rgb, quickFlash);
        setColor(colors);
    }

    /**
     * Actually sends the color to the Mi Band
     *
     * @param color
     */
    private void setColor(byte[] color) {
        checkConnection();

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, color));

        queue(list);
    }

    private byte[] convertRgb(int rgb) {
        return convertRgb(rgb, true);
    }

    private byte[] convertRgb(int rgb, boolean quickFlash) {
        final int red = ((rgb >> 16) & 0x0ff) / 42;
        final int green = ((rgb >> 8) & 0x0ff) / 42;
        final int blue = ((rgb) & 0x0ff) / 42;

        return new byte[]{14, (byte) red, (byte) green, (byte) blue, quickFlash ? (byte) 1 : (byte) 0};
    }

    /**
     * Sends a custom notification to the Mi Band
     */
    public synchronized void setLedColor(final int flashTimes, final int flashColour, final int flashDuration) {

        final List<BLEAction> list = new ArrayList<>();

        byte[] colors = convertRgb(flashColour);
        byte[] protocalOff = {14, colors[0], colors[1], colors[2], 0};

        for (int i = 1; i <= flashTimes; i++) {
            list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, colors));
            list.add(new WaitAction(flashDuration));
            list.add((new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, protocalOff)));
            list.add(new WaitAction(flashDuration));
        }

        queue(list);
    }

    public synchronized void notifyBand(final int flashColour) {
        List<BLEAction> list = new ArrayList<>();

        byte[] colors = convertRgb(flashColour);
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.VIBRATION_WITHOUT_LED));
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, colors));
        queue(list);
    }

    private void queue(List<BLEAction> list) {
        final BLETask task = new BLETask(list);

        try {
            io.queueTask(task);
        } catch (NullPointerException ignored) {

        }
    }

    /**
     * Notifies the Mi Band with vibration and colour.
     * Vibrate and flashes the colour "times" times. Each iteration will start "on_time" milliseconds (up to 500, will be truncated if larger), and then stop it "off_time" milliseconds (no limit here).
     *
     * @param times       : the amount of times to vibrate
     * @param onTime      : the time in milliseconds that each vibration will last (maximum of 500 milliseconds). Preferably more than 100 milliseconds
     * @param offTime     : the time in milliseconds that each cycle will last
     * @param flashColour int value of the colour to flash
     */
    public synchronized void notifyBand(final int times, final int onTime, final int offTime, final int flashColour) {
        //final int newOnTime = Math.min(onTime, 500);

        final List<BLEAction> list = new ArrayList<>();

        byte[] colors = convertRgb(flashColour);

        list.add(new WaitAction(150));
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.VIBRATION_WITHOUT_LED));
        list.add(new WaitAction(300));
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, colors));

        queue(list);
    }

    /**
     * Sets up the user information. If there's no UserInfo provided, we create one by default
     *
     * @param userInfo
     */
    public void setUserInfo(UserInfo userInfo, ActionCallback callback) {
        checkConnection();

        BluetoothDevice device = btConnectionManager.getDevice();

        if (userInfo == null) {
            userInfo = UserInfo.getDefault(getAddress(), mDeviceInfo);
        }

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_USER_INFO, userInfo.getData(), callback));

        queue(list);
    }

    public void setUserInfo(int gender, int age, int height, int weight, String alias) {
        UserInfo user = UserInfo.create(btConnectionManager.getDevice().getAddress(), gender, age, height, weight, alias, 0, mDeviceInfo);

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_USER_INFO, user.getData()));

        queue(list);
    }

    /**
     * Your Mi Band will do crazy things (LED flashing, vibrate).
     * Note: This will remove bonding information on the Mi Band, which might confused Android.
     * So before you connect next time remove your Mi Band via Settings, Bluetooth.
     */
    public void selfTest() {
        checkConnection();

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_TEST, Protocol.SELF_TEST));

        queue(list);
    }

    public boolean isSyncNotification() {
        return btConnectionManager.isSyncNotification();
    }
}
