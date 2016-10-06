package de.max.miband.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import de.max.miband.ActionCallback;
import de.max.miband.DateUtils;
import de.max.miband.NotifyListener;
import de.max.miband.model.Profile;
import de.max.miband.model.Protocol;
import de.max.miband.models.ActivityData;
import de.max.miband.sqlite.ActivitySQLite;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BTCommandManager {

    private static final String TAG = BTCommandManager.class.getSimpleName();

    private ActionCallback currentCallback;

    private boolean deleteAfterSynch =false;

    private ActionCallback currentSynchCallback;
    private QueueConsumer mQueueConsumer;

    public HashMap<UUID, NotifyListener> notifyListeners = new HashMap<UUID, NotifyListener>();

    private Context context;
    public BluetoothGatt gatt;

    public void clearQueue() {
        this.mQueueConsumer.clear();
    }

    public void setSynchFail(boolean synchFail) {
        this.synchFail = synchFail;
    }

    private boolean synchFail = false;

    public BTCommandManager(Context context, BluetoothGatt gatt) {
        this.context = context;
        this.gatt = gatt;

        mQueueConsumer = new QueueConsumer(context, this);

        Thread t = new Thread(mQueueConsumer);
        t.start();
    }

    public void queueTask(final BLETask task) {
        mQueueConsumer.add(task);
    }

    public void setDeleteAfterSynch(boolean delete){
        this.deleteAfterSynch=delete;
    }

    public boolean isDeleteAfterSynch(){
        return this.deleteAfterSynch;
    }

    public QueueConsumer getmQueueConsumer() {
        return mQueueConsumer;
    }

    public void writeAndRead(final UUID uuid, byte[] valueToWrite, final ActionCallback callback) {
        ActionCallback readCallback = new ActionCallback() {

            @Override
            public void onSuccess(Object characteristic) {
                BTCommandManager.this.readCharacteristic(uuid, callback);
            }

            @Override
            public void onFail(int errorCode, String msg) {
                callback.onFail(errorCode, msg);
            }
        };
        this.writeCharacteristic(uuid, valueToWrite, readCallback);
    }

    /**
     * Sends a command to the Mi Band
     *
     * @param uuid     the {@link Profile} used
     * @param value    the values to send
     * @param callback
     */
    public void writeCharacteristic(UUID uuid, byte[] value, ActionCallback callback) {
        try {
            this.currentCallback = callback;
            BluetoothGattCharacteristic chara = gatt.getService(Profile.UUID_SERVICE_MILI).getCharacteristic(uuid);
            if (null == chara) {
                this.onFail(-1, "BluetoothGattCharacteristic " + uuid + " doesn't exist");
                return;
            }
            chara.setValue(value);
            if (!this.gatt.writeCharacteristic(chara)) {
                this.onFail(-1, "gatt.writeCharacteristic() return false");
            } else {
                onSuccess(chara);
            }
        } catch (Throwable tr) {
            Log.e(TAG, "writeCharacteristic", tr);
            this.onFail(-1, tr.getMessage());
            tr.printStackTrace();
        }
    }


    public void setCurrentSynchCallback(ActionCallback currentSynchCallback) {
        this.currentSynchCallback = currentSynchCallback;
    }

    public ActionCallback getCurrentSynchCallback() {
        return currentSynchCallback;
    }

    public boolean writeCharacteristicWithResponse(UUID service, UUID uuid, byte[] value, ActionCallback callback) {
        try {
            this.currentCallback = callback;
            BluetoothGattCharacteristic chara = gatt.getService(service).getCharacteristic(uuid);
            if (null == chara) {
                this.onFail(333, "Characteristic is null");
                return false;
            }
            chara.setValue(value);

            if (this.gatt.writeCharacteristic(chara)) {
                return true;
            } else {
                this.onFail(333, "Write Charactersitic failed");
                return false;
            }
        } catch (Throwable tr) {
            tr.printStackTrace();
            this.onFail(333, "Error Writing Characteristic");
            return false;
        }
    }

    public boolean writeCharacteristicWithResponse(UUID uuid, byte[] value, ActionCallback callback) {
        try {
            this.currentCallback = callback;

            BluetoothGattCharacteristic chara = gatt.getService(Profile.UUID_SERVICE_MILI).getCharacteristic(uuid);
            if (null == chara) {
                this.onFail(333, "Characteristic is null");
                return false;
            }
            chara.setValue(value);

            if (this.gatt.writeCharacteristic(chara)) {
                return true;

            } else {
                this.onFail(333, "Write Charactersitic failed");
                return false;
            }

        } catch (Throwable tr) {
            tr.printStackTrace();
            this.onFail(333, "Error Writing Characteristic");
            return false;

        }
    }

    /**
     * Reads a command from the Mi Band
     *
     * @param uuid     the {@link Profile} used
     * @param callback
     */
    public void readCharacteristic(UUID uuid, ActionCallback callback) {
        try {
            this.currentCallback = callback;
            BluetoothGattCharacteristic chara = gatt.getService(Profile.UUID_SERVICE_MILI).getCharacteristic(uuid);
            if (null == chara) {
                this.onFail(-1, "BluetoothGattCharacteristic " + uuid + " doesn't exist");
                return;
            }
            if (!this.gatt.readCharacteristic(chara)) {
                this.onFail(-1, "gatt.readCharacteristic() return false");
            }
        } catch (Throwable tr) {
            tr.printStackTrace();
            Log.e(TAG, "readCharacteristic", tr);
            this.onFail(-1, tr.getMessage());
        }
    }

    public boolean readCharacteristicWithResponse(UUID uuid, ActionCallback callback) {
        try {
            this.currentCallback = callback;
            BluetoothGattCharacteristic chara = gatt.getService(Profile.UUID_SERVICE_MILI).getCharacteristic(uuid);
            if (null == chara) {
                return false;
            }
            return this.gatt.readCharacteristic(chara);
        } catch (Throwable tr) {
            tr.printStackTrace();
            Log.e(TAG, "readCharacteristic", tr);
            onFail(333, "read Characteristic fail");
            return false;
        }
    }

    /**
     * Reads the bluetooth's received signal strength indication
     *
     * @param callback
     */
    public void readRssi(ActionCallback callback) {
        try {
            this.currentCallback = callback;
            this.gatt.readRemoteRssi();
        } catch (Throwable tr) {
            tr.printStackTrace();
            Log.e(TAG, "readRssi", tr);
            this.onFail(-1, tr.getMessage());
        }
    }

    public void setNotifyListener(UUID characteristicId, NotifyListener listener) {
        if (this.notifyListeners.containsKey(characteristicId))
            return;

        BluetoothGattCharacteristic chara = gatt.getService(Profile.UUID_SERVICE_MILI).getCharacteristic(characteristicId);
        if (chara == null)
            return;

        this.gatt.setCharacteristicNotification(chara, true);
        BluetoothGattDescriptor descriptor = chara.getDescriptor(Profile.UUID_DESCRIPTOR_UPDATE_NOTIFICATION);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        this.gatt.writeDescriptor(descriptor);
        this.notifyListeners.put(characteristicId, listener);
    }


    private byte[] getLatency(int minConnectionInterval, int maxConnectionInterval, int latency, int timeout, int advertisementInterval) {
        byte result[] = new byte[12];
        result[0] = (byte) (minConnectionInterval & 0xff);
        result[1] = (byte) (0xff & minConnectionInterval >> 8);
        result[2] = (byte) (maxConnectionInterval & 0xff);
        result[3] = (byte) (0xff & maxConnectionInterval >> 8);
        result[4] = (byte) (latency & 0xff);
        result[5] = (byte) (0xff & latency >> 8);
        result[6] = (byte) (timeout & 0xff);
        result[7] = (byte) (0xff & timeout >> 8);
        result[8] = 0;
        result[9] = 0;
        result[10] = (byte) (advertisementInterval & 0xff);
        result[11] = (byte) (0xff & advertisementInterval >> 8);

        return result;
    }


    public byte[] getLowLatency() {
        int minConnectionInterval = 39;
        int maxConnectionInterval = 49;
        int latency = 0;
        int timeout = 500;
        int advertisementInterval = 0;

        return getLatency(minConnectionInterval, maxConnectionInterval, latency, timeout, advertisementInterval);
    }

    public byte[] getHighLatency() {
        int minConnectionInterval = 460;
        int maxConnectionInterval = 500;
        int latency = 0;
        int timeout = 500;
        int advertisementInterval = 0;

        return getLatency(minConnectionInterval, maxConnectionInterval, latency, timeout, advertisementInterval);
    }

    public void onSuccess(Object data) {
        if (data.equals("sync complete") && this.currentSynchCallback != null) {
            this.currentSynchCallback.onSuccess("sync complete");
            this.currentSynchCallback = null;
        }

        if (this.currentCallback != null) {
            ActionCallback callback = this.currentCallback;
            this.currentCallback = null;
            callback.onSuccess(data);
        }
    }

    public void onFail(int errorCode, String msg) {
        this.synchFail = true;
        handleActivityFetchFinish();
        if (this.currentCallback != null) {
            ActionCallback callback = this.currentCallback;
            this.currentCallback = null;
            callback.onFail(errorCode, msg);
        }

        if (this.currentSynchCallback != null) {
            this.currentSynchCallback.onFail(errorCode, msg);
            this.currentSynchCallback = null;
        }
    }

    public void handleControlPointResult(byte[] value) {
        if (value != null) {
            for (byte b : value) {
                Log.i(TAG, "handleControlPoint GOT DATA:" + String.format("0x%8x", b));
            }
        } else {
            Log.e(TAG, "handleControlPoint GOT null");
        }
    }

    public void handlePairResult(byte[] pairResult) {
        String value = null;
        if (pairResult != null) {
            if (pairResult.length == 1) {
                try {
                    if (pairResult[0] == 2) {
                        onSuccess(pairResult);
                        Log.d(TAG, "Successfully paired  MI device BT COMMAND");
                        return;
                    }
                } catch (Exception ex) {
                    onFail(333, "Error identifying pairing result");
                    Log.e(TAG, "Error identifying pairing result", ex);
                    return;
                }
            }
            value = Arrays.toString(pairResult);
        }
        Log.d(TAG, "MI Band pairing result in BT COMMAND: " + value);
    }

    //ACTIVITY DATA
    //temporary buffer, size is a multiple of 60 because we want to store complete minutes (1 minute = 3 bytes)
    private static final int activityDataHolderSize = 3 * 60 * 4; // 8h

    private static class ActivityStruct {
        private final byte[] activityDataHolder;
        private final int activityDataHolderSize;
        //index of the buffer above
        private int activityDataHolderProgress = 0;
        //number of bytes we will get in a single data transfer, used as counter
        private int activityDataRemainingBytes = 0;
        //same as above, but remains untouched for the ack message
        private int activityDataUntilNextHeader = 0;
        //timestamp of the single data transfer, incremented to store each minute's data
        private GregorianCalendar activityDataTimestampProgress = null;
        //same as above, but remains untouched for the ack message
        private GregorianCalendar activityDataTimestampToAck = null;

        ActivityStruct(int activityDataHolderSize) {
            this.activityDataHolderSize = activityDataHolderSize;
            activityDataHolder = new byte[activityDataHolderSize];
        }

        public boolean hasRoomFor(byte[] value) {
            return activityDataRemainingBytes >= value.length;
        }

        public boolean isValidData(byte[] value) {
            //I don't like this clause, but until we figure out why we get different data sometimes this should work
            return value.length == 20 || value.length == activityDataRemainingBytes;
        }

        public boolean isBufferFull() {
            return activityDataHolderSize == activityDataHolderProgress;
        }

        public void buffer(byte[] value) {
            System.arraycopy(value, 0, activityDataHolder, activityDataHolderProgress, value.length);
            activityDataHolderProgress += value.length;
            activityDataRemainingBytes -= value.length;

            validate();
        }

        private void validate() {
            if (activityDataRemainingBytes < 0) {
                throw new AssertionError("Illegal state, remaining bytes is negative");
            }
        }

        public boolean isFirstChunk() {
            return activityDataTimestampProgress == null;
        }

        public void startNewBlock(GregorianCalendar timestamp, int dataUntilNextHeader) {
            if (timestamp == null) {
                throw new AssertionError("Timestamp must not be null");
            }

            if (isFirstChunk()) {
                activityDataTimestampProgress = timestamp;
            } else {
                if (timestamp.getTimeInMillis() >= activityDataTimestampProgress.getTimeInMillis()) {
                    activityDataTimestampProgress = timestamp;
                } else {
                    // something is fishy here... better not trust the given timestamp and simply
                    // (re)use the current one
                    // we do accept the timestamp to ack though, so that the bogus data is properly cleared on the band
                    Log.e(TAG, "Got bogus timestamp: " + timestamp.getTime() + " that is smaller than the previous timestamp: " + activityDataTimestampProgress.getTime());
                }
            }
            activityDataTimestampToAck = (GregorianCalendar) timestamp.clone();
            activityDataRemainingBytes = activityDataUntilNextHeader = dataUntilNextHeader;
            validate();
        }

        public boolean isBlockFinished() {
            return activityDataRemainingBytes == 0;
        }

        public void bufferFlushed(int minutes) {
            activityDataTimestampProgress.add(Calendar.MINUTE, minutes);
            activityDataHolderProgress = 0;
        }
    }

    private ActivityStruct activityStruct;

    public void handleActivityNotif(byte[] value) {
        boolean firstChunk = activityStruct == null && !this.synchFail;
        if (firstChunk) {
            activityStruct = new ActivityStruct(3 * 60 * 4);
        }

        if (!this.synchFail) {
            if (value.length == 11) {
                handleActivityMetadata(value);
            } else {
                bufferActivityData(value);
            }

            if (activityStruct.isBlockFinished()) {
                sendAckDataTransfer(activityStruct.activityDataTimestampToAck, activityStruct.activityDataUntilNextHeader);
                //GB.updateTransferNotification("", false, 100, getContext());
            }

        /*
        } finally {
            if (activityStruct != null && !synchFail) {
                if (activityStruct.isBlockFinished()) {
                    sendAckDataTransfer(activityStruct.activityDataTimestampToAck, activityStruct.activityDataUntilNextHeader);
                }
            } else {
                final List<BLEAction> list = new ArrayList<>();
                list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.COMMAND_STOP_SYNC_DATA));
                final BLETask task = new BLETask(list);
                queueTask(task);

                //Set to High Latency again
                final List<BLEAction> list3 = new ArrayList<>();
                list3.add(new WriteAction(Profile.UUID_CHAR_LE_PARAMS, getHighLatency()));

                BLETask task3 = new BLETask(list3);
                queueTask(task3);

                Log.e(TAG, "SYNCHO STOPPED AND NOT COMPLETED");
            }
        }

        //Log.d(TAG, "activity data: length: " + value.length + ", remaining bytes: " + activityStruct.activityDataRemainingBytes);
        */

        }else{
            final List<BLEAction> list2 = new ArrayList<>();
            list2.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.COMMAND_STOP_SYNC_DATA));
            BLETask task2 = new BLETask(list2);
            queueTask(task2);

            Log.e(TAG, "SYNCHO STOPPED AND NOT COMPLETED");
            activityStruct = null;
        }
    }


    public void handleNotificationNotif(byte[] value) {
        if (value.length != 1) {
            Log.e(TAG, "Notifications should be 1 byte long.");
            Log.d(TAG, "RECEIVED DATA WITH LENGTH: " + value.length);
            for (byte b : value) {
                Log.d(TAG, "DATA: " + String.format("0x%2x", b));
            }
            return;
        }
        switch (value[0]) {
            //AUTH FAILED
            case 0x6:
                // we get first FAILED, then NOTIFY_STATUS_MOTOR_AUTH (0x13)
                // which means, we need to authenticate by tapping
                Log.e(TAG, "BAND NEEDS PAIRING");
                break;
            //SUCCESS
            case 0x5: // fall through -- not sure which one we get
            case 0xa: // for Mi 1A
            case 0x15:
                Log.d(TAG, "Band successfully authenticated");
                break;

            //STATUS MOTOR AUTH
            case 0x13:
                Log.d(TAG, "Band needs authentication (MOTOR_AUTH)");
                break;

            default:
                for (byte b : value) {
                    Log.d(TAG, "DATA: " + String.format("0x%2x", b));
                }
        }
    }

    private void handleActivityMetadata(byte[] value) {
        if (value.length != 11) {
            return;
        }

        // byte 0 is the data type: 1 means that each minute is represented by a triplet of bytes
        int dataType = value[0];
        // byte 1 to 6 represent a timestamp
        GregorianCalendar timestamp = MiBandDateConverter.rawBytesToCalendar(value, 1);

        // counter of all data held by the band
        int totalDataToRead = (value[7] & 0xff) | ((value[8] & 0xff) << 8);
        totalDataToRead *= (dataType == Protocol.MODE_REGULAR_DATA_LEN_MINUTE) ? 3 : 1;


        // counter of this data block
        int dataUntilNextHeader = (value[9] & 0xff) | ((value[10] & 0xff) << 8);
        dataUntilNextHeader *= (dataType == Protocol.MODE_REGULAR_DATA_LEN_MINUTE) ? 3 : 1;

        // there is a total of totalDataToRead that will come in chunks (3 or 4 bytes per minute if dataType == 1 (MiBandService.MODE_REGULAR_DATA_LEN_MINUTE)),
        // these chunks are usually 20 bytes long and grouped in blocks
        // after dataUntilNextHeader bytes we will get a new packet of 11 bytes that should be parsed
        // as we just did

        Log.d(TAG, "total data to read: " + totalDataToRead + " len: " + (totalDataToRead / 3) + " minute(s)");
        Log.d(TAG, "data to read until next header: " + dataUntilNextHeader + " len: " + (dataUntilNextHeader / 3) + " minute(s)");
        Log.d(TAG, "TIMESTAMP: " + DateFormat.getDateTimeInstance().format(timestamp.getTime()) + " magic byte: " + dataUntilNextHeader);
        if (activityStruct != null) {
            activityStruct.startNewBlock(timestamp, dataUntilNextHeader);
        } else {
            this.onFail(333, "ActivityStruct is null");
        }
    }


    private void bufferActivityData(byte[] value) {
        if (activityStruct.hasRoomFor(value)) {
            if (activityStruct.isValidData(value)) {
                activityStruct.buffer(value);

                if (activityStruct.isBufferFull()) {
                    flushActivityDataHolder();
                }
            } else {
                // the length of the chunk is not what we expect. We need to make sense of this data
                Log.e(TAG, "GOT UNEXPECTED ACTIVITY DATA WITH LENGTH: " + value.length + ", EXPECTED LENGTH: " + activityStruct.activityDataRemainingBytes);
            }
        } else {
            handleActivityFetchFinish();
            Log.e(TAG, "error buffering activity data: remaining bytes: " + activityStruct.activityDataRemainingBytes + ", received: " + value.length);
            try {
                final List<BLEAction> list = new ArrayList<>();
                list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.COMMAND_STOP_SYNC_DATA));
                final BLETask task = new BLETask(list);
                queueTask(task);

                onFail(333, "error buffering activity data: remaining bytes:" + activityStruct.activityDataRemainingBytes + ", received: " + value.length);

            } catch (Exception e) {
                onFail(333, "error stopping activity sync");
                Log.e(TAG, "error stopping activity sync", e);
                e.printStackTrace();
            }
        }
    }

    private void handleActivityFetchFinish() {
        Log.d(TAG, "Fetching activity data has finished.");
        activityStruct = null;
    }

    private void flushActivityDataHolder() {
        if (activityStruct == null) {
            Log.d(TAG, "nothing to flush, struct is already null");
            return;
        }

        int minutes = 0;

        try {
            byte category, intensity, steps;
            ActivitySQLite dbHandler = ActivitySQLite.getInstance(context);
            int timestampInSeconds = (int) (activityStruct.activityDataTimestampProgress.getTimeInMillis() / 1000);

            for (int i = 0; i < activityStruct.activityDataHolderProgress; i += 3) { //TODO: check if multiple of 3, if not something is wrong
                category = activityStruct.activityDataHolder[i];
                intensity = activityStruct.activityDataHolder[i + 1];
                steps = activityStruct.activityDataHolder[i + 2];

                dbHandler.saveActivity(
                        timestampInSeconds,
                        ActivityData.PROVIDER_MIBAND,
                        intensity,
                        steps & 0xff,
                        category);


                //activityStruct.activityDataTimestampProgress.add(Calendar.MINUTE, 1);
                timestampInSeconds += 60;
                minutes++;
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally{
            if (activityStruct != null) {
                activityStruct.bufferFlushed(minutes);
            }
        }
        //activityStruct.activityDataHolderProgress = 0;
    }

    private int getMostRecentStep(){
        //all our data is stored in ActivitySQLite as ActivityData objects
        return ActivitySQLite.getInstance(context).getMostRecentActivity().getTimestamp();
    }

    private void sendAckDataTransfer(Calendar time, int bytesTransferred) {
        this.setDeleteAfterSynch(!this.synchFail);
        byte[] ackTime = MiBandDateConverter.calendarToRawBytes(time);

        byte[] ackChecksum = new byte[]{
                (byte) (~bytesTransferred & 0xff),
                (byte) (0xff & (~bytesTransferred >> 8))
        };

        //Get the most recent inserted step date to check if transmission wen well.
        int recentTS=getMostRecentStep();
        Log.d(TAG, "RECENT TS: "+recentTS);
        Log.d(TAG, "CURRENT TS: "+(int) (System.currentTimeMillis() / 1000));
        boolean delete=(((int) (System.currentTimeMillis() / 1000))- recentTS) < 125;
        if (delete){
            ackChecksum = new byte[]{
                (byte) (bytesTransferred & 0xff),
                (byte) (0xff & (bytesTransferred >> 8))
            };
        }


        byte[] ack = new byte[]{
                Protocol.COMMAND_CONFIRM_ACTIVITY_DATA_TRANSFER_COMPLETE,
                ackTime[0],
                ackTime[1],
                ackTime[2],
                ackTime[3],
                ackTime[4],
                ackTime[5],
                ackChecksum[0],
                ackChecksum[1]
        };

        final List<BLEAction> list = new ArrayList<>();
        list.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, ack));
        BLETask task = new BLETask(list);

        try {
            queueTask(task);
            // flush to the DB after sending the ACK
            flushActivityDataHolder();

            //The last data chunk sent by the miband has always length 0.
            //When we ack this chunk, the transfer is done.
            if (bytesTransferred == 0) {
                final List<BLEAction> list2 = new ArrayList<>();
                BLETask task2 = new BLETask(list2);
                if (!delete){
                    Log.d(TAG,"!NOT DELETING DATA!");
                    //Do not ACK synchronization (data remains on Device)
                    list2.add(new WriteAction(Profile.UUID_CHAR_CONTROL_POINT, Protocol.COMMAND_STOP_SYNC_DATA));
                    task2 = new BLETask(list2);
                }else{
                    Log.d(TAG,"!DELETING DATA!");
                }

                //Set to High Latency again
                /*
                final List<BLEAction> list3 = new ArrayList<>();
                list3.add(new WriteAction(Profile.UUID_CHAR_LE_PARAMS, getHighLatency()));

                BLETask task3 = new BLETask(list3);
                queueTask(task3);
                if (!this.isDeleteAfterSynch()) {
                    queueTask(task2);
                }
                */
                if (!delete) {
                    queueTask(task2);
                }
                handleActivityFetchFinish();
                onSuccess("sync complete");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            onFail(333, "Unable to send ack to MI");
            Log.e(TAG, "Unable to send ack to MI");
        }
    }
}
