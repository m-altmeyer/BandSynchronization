package de.max.miband.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import de.max.miband.ActionCallback;
import de.max.miband.AppUtils;
import de.max.miband.model.Profile;
import de.max.miband.model.UserInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Max on 5/26/16.
 */
public class BTConnectionManager {

    public interface DataRead {
        public void OnDataRead();
    }

    //the scanning timeout period
    private static final long SCAN_PERIOD = 45000;
    private static BTConnectionManager instance;
    private final String TAG = getClass().getSimpleName();
    private Context context;
    private boolean mScanning = false;
    private boolean mFound = false;
    private boolean mAlreadyPaired = false;
    private boolean isConnected = false;

    private boolean isConnecting = false;
    private boolean isSyncNotification = false;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private ActionCallback connectionCallback;

    private BTCommandManager io;
    private BluetoothGatt gatt;

    private boolean realTimeStepsEnabled=false;
    private boolean sensorDataEnabled=false;
    private boolean activitySynchronizationEnabled=false;
    private DataRead onDataRead;



    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

            Log.d(TAG,
                    "onLeScan: name: " + device.getName() + ", uuid: "
                            + device.getUuids() + ", add: "
                            + device.getAddress() + ", type: "
                            + device.getType() + ", bondState: "
                            + device.getBondState() + ", rssi: " + rssi);

            if (device.getName() != null && device.getAddress() != null && device.getName().startsWith("MI")){ //&& device.getAddress().startsWith("88:0F:10")) {
                mFound = true;

                stopDiscovery();

                device.connectGatt(context, false, btleGattCallback);
            }

            if (isConnected()){
                stopDiscovery();
            }
        }
    };

    private Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            stopDiscovery();
        }
    };

    public BTConnectionManager(Context context, ActionCallback connectionCallback) {
        this.context = context;

        //Log.i(TAG, "new BTConnectionManager");

        this.connectionCallback = connectionCallback;
    }

    public synchronized static BTConnectionManager getInstance(Context context, ActionCallback connectionCallback) {
        if (instance == null) {
            instance = new BTConnectionManager(context, connectionCallback);
        }

        return instance;
    }

    public void connect(String address) {
        Log.i(TAG, "trying to connect to "+address);
        mFound = false;

        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            connectionCallback.onFail(NotificationConstants.BLUETOOTH_OFF, "Bluetooth disabled");
            isConnected = false;
            isConnecting = false;
        } else {

            if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                connectionCallback.onFail(NotificationConstants.BLUETOOTH_OFF, "Bluetooth LE not supported");
                isConnected = false;
                isConnecting = false;
                return;
            }

            if (!isConnecting && !adapter.isDiscovering()) {

                Log.i(TAG, "connecting...");

                isConnecting = true;

                BluetoothDevice mBluetoothMi = adapter.getRemoteDevice(address);
                mBluetoothMi.connectGatt(context, false, btleGattCallback);
                /*
                if (!tryPairedDevices()) {

                    Log.i(TAG, "not already paired");
                    mScanning = true;

                    if (AppUtils.supportsBluetoothLE(context)) {
                        //Log.i(TAG, "is BTLE");
                        adapter.stopLeScan(mLeScanCallback);
                        startBTLEDiscovery();
                    } else {
                        //Log.i(TAG, "is BT");
                        adapter.cancelDiscovery();
                        startBTDiscovery();
                    }
                }
                */
            }
        }
    }

    public boolean isRealTimeStepsEnabled() {
        return realTimeStepsEnabled;
    }

    public boolean isSensorDataEnabled() {
        return sensorDataEnabled;
    }

    public boolean isActivitySynchronizationEnabled() {
        return activitySynchronizationEnabled;
    }

    public void setRealTimeStepsEnabled(boolean realTimeStepsEnabled) {
        this.realTimeStepsEnabled = realTimeStepsEnabled;
    }

    public void setSensorDataEnabled(boolean sensorDataEnabled) {
        this.sensorDataEnabled = sensorDataEnabled;
    }

    public void setActivitySynchronizationEnabled(boolean activitySynchronizationEnabled) {
        this.activitySynchronizationEnabled = activitySynchronizationEnabled;
    }



    public void enableSynchronization( boolean enable) {
        if (gatt == null) {
            Log.e(TAG,"NO GATT!!");
            return;
        }

        HashMap<UUID, BluetoothGattCharacteristic> mAvailableCharacteristics = null;

        for (BluetoothGattService service : gatt.getServices()) {
            if (Profile.UUID_SERVICE_MILI.equals(service.getUuid())) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                if (characteristics == null || characteristics.isEmpty()) {
                    Log.e(TAG, "Supported LE service " + service.getUuid() + "did not return any characteristics");
                    continue;
                }
                mAvailableCharacteristics = new HashMap<>(characteristics.size());
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    mAvailableCharacteristics.put(characteristic.getUuid(), characteristic);
                }
            }
        }

        try {
            if (mAvailableCharacteristics != null && !mAvailableCharacteristics.isEmpty()) {

                isSyncNotification = enable;

                final List<BLEAction> list1 = new ArrayList<>();
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_REALTIME_STEPS), false));
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_SENSOR_DATA), false));
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_ACTIVITY_DATA), enable));


                final BLETask task1 = new BLETask(list1);
                io.queueTask(task1);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }


    public void enableNotifications( boolean enable) {
        if (gatt == null) {
            Log.e(TAG,"NO GATT!!");
            return;
        }
        toggleNotifications(enable);
        HashMap<UUID, BluetoothGattCharacteristic> mAvailableCharacteristics = null;

        for (BluetoothGattService service : gatt.getServices()) {
            if (Profile.UUID_SERVICE_MILI.equals(service.getUuid())) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                if (characteristics == null || characteristics.isEmpty()) {
                    Log.e(TAG, "Supported LE service " + service.getUuid() + "did not return any characteristics");
                    continue;
                }
                mAvailableCharacteristics = new HashMap<>(characteristics.size());
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    mAvailableCharacteristics.put(characteristic.getUuid(), characteristic);
                }
            }
        }

        try {
            if (mAvailableCharacteristics != null && !mAvailableCharacteristics.isEmpty()) {

                isSyncNotification = enable;

                final List<BLEAction> list1 = new ArrayList<>();
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_NOTIFICATION), enable));

                final BLETask task1 = new BLETask(list1);
                io.queueTask(task1);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }


    public void enableRealtimeNotifications( boolean enable) {
        if (gatt == null) {
            Log.e(TAG,"NO GATT!!");
            return;
        }
        toggleNotifications(enable);
        HashMap<UUID, BluetoothGattCharacteristic> mAvailableCharacteristics = null;

        for (BluetoothGattService service : gatt.getServices()) {
            if (Profile.UUID_SERVICE_MILI.equals(service.getUuid())) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                if (characteristics == null || characteristics.isEmpty()) {
                    Log.e(TAG, "Supported LE service " + service.getUuid() + "did not return any characteristics");
                    continue;
                }
                mAvailableCharacteristics = new HashMap<>(characteristics.size());
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    mAvailableCharacteristics.put(characteristic.getUuid(), characteristic);
                }
            }
        }

        try {
            if (mAvailableCharacteristics != null && !mAvailableCharacteristics.isEmpty()) {

                isSyncNotification = enable;

                final List<BLEAction> list1 = new ArrayList<>();
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_REALTIME_STEPS), enable));
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_SENSOR_DATA), enable));
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_ACTIVITY_DATA), false));
                list1.add(new NotifyAction(mAvailableCharacteristics.get(Profile.UUID_CHAR_SENSOR_DATA), enable));


                final BLETask task1 = new BLETask(list1);
                io.queueTask(task1);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void disconnect (boolean disconnectGatt){
        if (gatt != null && disconnectGatt) {
            gatt.disconnect();
            gatt=null;
        }
        disconnect();
    }

    public void disconnect() {
        isConnected = false;
        isConnecting = false;

        try{
            io.getmQueueConsumer().abort();
            if (io.getCurrentSynchCallback()!=null){
                io.getCurrentSynchCallback().onFail(333,"Connection lost");
            }
            io.onFail(333, "Connection lost");

            connectionCallback.onFail(-1, "disconnected");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public void toggleNotifications(boolean enable) {
        if (gatt == null) return;

        HashMap<UUID, BluetoothGattCharacteristic> mAvailableCharacteristics = null;

        for (BluetoothGattService service : gatt.getServices()) {
            if (Profile.UUID_SERVICE_MILI.equals(service.getUuid())) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                if (characteristics == null || characteristics.isEmpty()) {
                    Log.e(TAG, "Supported LE service " + service.getUuid() + "did not return any characteristics");
                    continue;
                }
                mAvailableCharacteristics = new HashMap<>(characteristics.size());
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    mAvailableCharacteristics.put(characteristic.getUuid(), characteristic);
                }
            }
        }

        try {
            if (mAvailableCharacteristics != null && !mAvailableCharacteristics.isEmpty()) {
                gatt.setCharacteristicNotification(mAvailableCharacteristics.get(Profile.UUID_CHAR_NOTIFICATION), enable);
                gatt.setCharacteristicNotification(mAvailableCharacteristics.get(Profile.UUID_CHAR_REALTIME_STEPS), enable);
                gatt.setCharacteristicNotification(mAvailableCharacteristics.get(Profile.UUID_CHAR_ACTIVITY_DATA), enable);
                gatt.setCharacteristicNotification(mAvailableCharacteristics.get(Profile.UUID_CHAR_BATTERY), enable);
                gatt.setCharacteristicNotification(mAvailableCharacteristics.get(Profile.UUID_CHAR_SENSOR_DATA), enable);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void dispose() {
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }

        isConnected = false;
        isConnecting = false;

        connectionCallback.onFail(-1, "disconnected");
    }


    public boolean isConnected() {
        return isConnected && adapter.isEnabled();
    }

    public boolean isSyncNotification() {
        return isSyncNotification;
    }

    public BluetoothDevice getDevice() {
        return gatt.getDevice();
    }

    public BluetoothGatt getGatt() {
        return gatt;
    }

    public void setIo(BTCommandManager io) {
        this.io = io;
        onDataRead = io.getmQueueConsumer();
    }

    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //TODO
            super.onConnectionStateChange(gatt, status, newState);

            Log.e(TAG, "onConnectionStateChange (2): " + newState);

            BTConnectionManager.this.gatt = gatt;

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED && !isConnected()) {
                gatt.discoverServices();
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnect();
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect();
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnect();
                //TODO
                Log.e(TAG, "onConnectionStateChange disconnect: " + newState);
            }
        }



        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                stopDiscovery();

                //we set the Gatt instance
                BTConnectionManager.this.gatt = gatt;

                isConnected = true;
                isConnecting = false;

                connectionCallback.onSuccess(true);
            } else {
                disconnect();
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                if (io != null)
                    io.onSuccess(characteristic);
            } else {
                io.onFail(status, "onCharacteristicRead fail");
            }

            if (onDataRead != null)
                onDataRead.OnDataRead();
        }

        private boolean checkCorrectGattInstance(BluetoothGatt g, String where) {
            if (g != gatt && g != null) {
                Log.d(TAG,"Ignoring event from wrong BluetoothGatt instance: " + where + "; " + gatt);
                return false;
            }
            return true;
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "descriptor write: " + descriptor.getUuid());
            /*
            if (!checkCorrectGattInstance(gatt, "descriptor write")) {
                gatt.disconnect();
                return;
            }
            */

            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }


        private void checkWaitingCharacteristic(BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "failed btle action, aborting transaction: " + characteristic.getUuid() +status);
                io.getmQueueConsumer().abort();
            }

            if (characteristic != null && io.getmQueueConsumer().getmWaitCharacteristic() != null && characteristic.getUuid().equals(io.getmQueueConsumer().getmWaitCharacteristic())) {
                if (onDataRead != null)
                    onDataRead.OnDataRead();
            } else {
                if (io.getmQueueConsumer().getmWaitCharacteristic() != null) {
                    Log.e(TAG, "checkWaitingCharacteristic: mismatched characteristic received: " + ((characteristic != null && characteristic.getUuid() != null) ? characteristic.getUuid().toString() : "(null)"));
                }
            }

            if (characteristic != null ) {
                if (onDataRead != null)
                    onDataRead.OnDataRead();
            }
        }


        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "descriptor read: " + descriptor.getUuid() + status);
            if (!checkCorrectGattInstance(gatt, "descriptor read")) {
                gatt.disconnect();
                return;
            }

            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            //if status is 0, success on sending and received
            //Log.i(TAG, "handleControlPoint got status:" + status);

            if (BluetoothGatt.GATT_SUCCESS == status) {
                if (!characteristic.getUuid().equals(Profile.UUID_CHAR_PAIR)){
                    io.onSuccess(characteristic);
                }


                if (characteristic.getUuid().equals(Profile.UUID_CHAR_PAIR)) {
                    io.handlePairResult(characteristic.getValue());
                }

                if (characteristic.getUuid().equals(Profile.UUID_CHAR_CONTROL_POINT)) {
                    io.handleControlPointResult(characteristic.getValue());
                }

            } else {
                io.onFail(status, "onCharacteristicWrite fail");
            }

            if (onDataRead != null)
                onDataRead.OnDataRead();
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                io.onSuccess(rssi);
            } else {
                io.onFail(status, "onCharacteristicRead fail");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID characteristicUUID = characteristic.getUuid();
            if (Profile.UUID_CHAR_ACTIVITY_DATA.equals(characteristicUUID)) {
                Log.d(TAG, "ON CHARACTERSI CHANGED!!! - ACTIVITY!! -" + characteristicUUID.toString());
                io.handleActivityNotif(characteristic.getValue());
            } else {
                Log.d(TAG, "ON CHARACTERSI CHANGED!!! - NOTIF?!! -" + characteristicUUID.toString());
                if (io.notifyListeners.containsKey(characteristic.getUuid())) {
                    io.notifyListeners.get(characteristic.getUuid()).onNotify(characteristic.getValue());
                }

                if (Profile.UUID_CHAR_NOTIFICATION.equals(characteristicUUID)){
                    io.handleNotificationNotif(characteristic.getValue());
                }

                super.onCharacteristicChanged(gatt, characteristic);
            }
        }
    };

    /*
     *
     *
     * DISCOVERY REGION
     *
     *
     */

    private void stopDiscovery() {
        Log.i(TAG, "Stopping discovery");
        isConnecting = false;

        //if (mScanning) {
        if (AppUtils.supportsBluetoothLE(context)) {
            stopBTLEDiscovery();
        } else {
            stopBTDiscovery();
        }

        mHandler.removeMessages(0, stopRunnable);
        mScanning = false;
        /*
        if (!mFound)
            connectionCallback.onFail(-1, "No bluetooth devices");
            */
        //}
    }

    private void startBTDiscovery() {
        Log.i(TAG, "Starting BT Discovery");
        mHandler.removeMessages(0, stopRunnable);
        mHandler.sendMessageDelayed(getPostMessage(stopRunnable), SCAN_PERIOD);
        stopBTDiscovery();
        if (adapter.startDiscovery())
            Log.v(TAG, "starting scan");
    }

    private void startBTLEDiscovery() {
        Log.i(TAG, "Starting BTLE Discovery");
        mHandler.removeMessages(0, stopRunnable);
        mHandler.sendMessageDelayed(getPostMessage(stopRunnable), SCAN_PERIOD);
        stopBTLEDiscovery();
        if (adapter.startLeScan(mLeScanCallback))
            Log.v(TAG, "starting scan");
    }

    private void stopBTLEDiscovery() {
        if (adapter.isDiscovering())
            adapter.stopLeScan(mLeScanCallback);
    }

    private void stopBTDiscovery() {
        if (adapter.isDiscovering())
            adapter.cancelDiscovery();
    }

    private Message getPostMessage(Runnable runnable) {
        Message m = Message.obtain(mHandler, runnable);
        m.obj = runnable;
        return m;
    }
}
