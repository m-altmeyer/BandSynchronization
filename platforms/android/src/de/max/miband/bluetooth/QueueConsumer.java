package de.max.miband.bluetooth;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Lewis on 10/01/15.
 */
public class QueueConsumer implements Runnable, BTConnectionManager.DataRead {
    private String TAG = this.getClass().getSimpleName();

    private BTCommandManager bleCommandManager;
    private Context context;
    private volatile boolean mAbortTransaction;
    private final LinkedBlockingQueue<BLETask> queue;

    public UUID getmWaitCharacteristic() {
        return mWaitCharacteristic;
    }

    private UUID mWaitCharacteristic;

    private CountDownLatch mWaitForActionResultLatch;

    public QueueConsumer(Context context, final BTCommandManager bleCommandManager) {
        this.context = context;
        this.bleCommandManager = bleCommandManager;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void add(final BLETask task) {
        queue.add(task);
    }

    public void abort(){
        mAbortTransaction = true;
    }

    public void clear(){
        queue.clear();
    }

    @Override
    public void run() {
        while (BTConnectionManager.getInstance(context, null).isConnected()) {
            try {
                final BLETask task = queue.take();

                final List<BLEAction> actions = task.getActions();
                mAbortTransaction = false;

                for (BLEAction action : actions) {
                    if (mAbortTransaction) { // got disconnected
                        Log.d(TAG, "Aborting running transaction");
                        break;
                    }

                    mWaitCharacteristic = action.getCharacteristic();

                    mWaitForActionResultLatch = new CountDownLatch(1);

                    if (action.run(bleCommandManager)) {
                        boolean waitForResult = action.expectsResult();
                        if (waitForResult) {
                            Log.d(TAG, "Latch Counter is "+mWaitForActionResultLatch.getCount());
                            mWaitForActionResultLatch.await();
                            mWaitForActionResultLatch = null;
                            if (mAbortTransaction) {
                                break;
                            }
                        }
                    } else {
                        Log.v(TAG, "action " + action.getClass().getSimpleName() + " returned false");
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());

            } finally {
                mWaitForActionResultLatch = null;
                mWaitCharacteristic = null;

                /*
                if (queue.isEmpty()) {
                    bleCommandManager.setHighLatency();
                }
                */
            }
        }

        if (mWaitForActionResultLatch != null){
            mWaitForActionResultLatch.countDown();
        }


        if (!queue.isEmpty()) {
            Log.d(TAG, "CLEARING QUEUE!!");
            queue.clear();
        }
    }

    @Override
    public void OnDataRead() {
        if (mWaitForActionResultLatch != null)
            mWaitForActionResultLatch.countDown();
    }
}
