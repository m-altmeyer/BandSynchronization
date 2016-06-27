package de.max.miband.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Log;

import java.util.UUID;

/**
 * Created by Max on 24.05.2016.
 */
public class NotifyAction implements BLEAction {

    public static final UUID UUID_DESCRIPTOR_GATT_CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString((String.format("0000%s-0000-1000-8000-00805f9b34fb", "2902")));

    protected final boolean enableFlag;
    private boolean hasWrittenDescriptor = false;
    private final String TAG = getClass().getSimpleName();
    private BluetoothGattCharacteristic characteristic;

    public NotifyAction(BluetoothGattCharacteristic characteristic, boolean enable) {
        this.characteristic=characteristic;
        enableFlag = enable;
    }


    @Override
    public boolean expectsResult() {
        return true;
    }


    public UUID getCharacteristic() {
            return characteristic.getUuid();
    }

    @Override
    public boolean run(BTCommandManager btCommandManager) {
        boolean result = btCommandManager.gatt.setCharacteristicNotification(characteristic, enableFlag);
        if (result) {
            BluetoothGattDescriptor notifyDescriptor = characteristic.getDescriptor(UUID_DESCRIPTOR_GATT_CLIENT_CHARACTERISTIC_CONFIGURATION);
            if (notifyDescriptor != null) {
                int properties = characteristic.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    Log.d(TAG, "use NOTIFICATION");
                    notifyDescriptor.setValue(enableFlag ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    result = btCommandManager.gatt.writeDescriptor(notifyDescriptor);
                    hasWrittenDescriptor = true;
                } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    Log.d(TAG, "use INDICATION");
                    notifyDescriptor.setValue(enableFlag ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    result = btCommandManager.gatt.writeDescriptor(notifyDescriptor);
                    hasWrittenDescriptor = true;
                } else {
                    hasWrittenDescriptor = false;
                }
            } else {
                Log.e(TAG,"sleep descriptor null");
                hasWrittenDescriptor = false;
            }
        } else {
            hasWrittenDescriptor = false;
            Log.e(TAG, "Unable to enable notification for " + characteristic.getUuid());
        }

        Log.d(TAG, "NotifyAction returned "+result);
        return result;
    }
}
