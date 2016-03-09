package io.roadsense.roadsenseclient;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.roadsense.roadsenseclient.common.BluetoothGATTDefines;
import io.roadsense.roadsenseclient.common.BluetoothLeService;
import io.roadsense.roadsenseclient.common.GattInfo;
import io.roadsense.roadsenseclient.common.GenericBluetoothProfile;
import io.roadsense.roadsenseclient.common.SensorTagMovementProfile;

/**
 * Created by emi on 08/03/16.
 */
public class AccQuery {
    private static final String TAG = "AccQuery";
    private Context context;
    private BluetoothLeService btleService;
    private BluetoothDevice btDevice;
    private List<GenericBluetoothProfile> mProfiles;
    private BluetoothGatt btGatt;

    public AccQuery(Context context, BluetoothLeService btleService, BluetoothDevice btDevice) {
        this.context = context;
        this.btDevice = btDevice;
        this.btleService = btleService;
        this.mProfiles = new ArrayList<>();

        context.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        // GATT database
        Resources res = context.getResources();
        XmlResourceParser xpp = res.getXml(R.xml.gatt_uuid);
        new GattInfo(xpp);

        btGatt = BluetoothLeService.getBtGatt();

        Log.w(TAG, "Discovering services...");
        btGatt.discoverServices();
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter fi = new IntentFilter();
        fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
        fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
        fi.addAction(BluetoothLeService.ACTION_DATA_READ);
        return fi;
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        List<BluetoothGattService> serviceList;
        List <BluetoothGattCharacteristic> charList = new ArrayList<BluetoothGattCharacteristic>();

        @Override
        public void onReceive(final Context context, Intent intent) {
            final String action = intent.getAction();
            final int status = intent.getIntExtra(BluetoothLeService.EXTRA_STATUS,
                    BluetoothGatt.GATT_SUCCESS);

            //Log.e(TAG, "Received");

            if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    serviceList = btleService.getSupportedGattServices();
                    if (serviceList.size() > 0) {
                        for (int ii = 0; ii < serviceList.size(); ii++) {
                            BluetoothGattService s = serviceList.get(ii);
                            List<BluetoothGattCharacteristic> c = s.getCharacteristics();
                            if (c.size() > 0) {
                                for (int jj = 0; jj < c.size(); jj++) {
                                    charList.add(c.get(jj));
                                }
                            }
                        }
                    }
                    Log.d(TAG,"Total characteristics " + charList.size());
                    Thread worker = new Thread(new Runnable() {
                        @Override
                        public void run() {

                            //Iterate through the services and add GenericBluetoothServices for each service
                            int nrNotificationsOn = 0;
                            int maxNotifications;
                            int servicesDiscovered = 0;
                            int totalCharacteristics = 0;
                            //serviceList = mBtLeService.getSupportedGattServices();
                            for (BluetoothGattService s : serviceList) {
                                List<BluetoothGattCharacteristic> chars = s.getCharacteristics();
                                totalCharacteristics += chars.size();
                            }

                            if (totalCharacteristics == 0) {
                                //Something bad happened, we have a problem
                                Log.e(TAG, "OH NO! NO characteristics.");
                            }

                            for (int ii = 0; ii < serviceList.size(); ii++) {
                                BluetoothGattService s = serviceList.get(ii);
                                List<BluetoothGattCharacteristic> chars = s.getCharacteristics();
                                if (chars.size() == 0) {
                                    Log.d(TAG, "No characteristics found for this service !!!");
                                    return;
                                }
                                servicesDiscovered++;
                                final float serviceDiscoveredcalc = (float)servicesDiscovered;
                                final float serviceTotalcalc = (float)serviceList.size();

                                Log.d(TAG, "Configuring service with uuid : " + s.getUuid().toString());

                                if (SensorTagMovementProfile.isCorrectService(s)) {
                                    SensorTagMovementProfile acc = new SensorTagMovementProfile(context, btDevice, s, btleService);
                                    mProfiles.add(acc);
                                    Log.d(TAG, "Found Motion !");
                                }
                            }
                            Log.d(TAG, "Enumerated services");
                            for (final GenericBluetoothProfile p : mProfiles) {
                                p.enableService();
                                p.onResume();
                                Log.d(TAG, "Enabling service.");
                            }
                        }
                    });
                    worker.start();
                } else {
                    Log.e(TAG, "Service discovery failed");
                }
            } else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)) {
                // Notification
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                //Log.d(TAG,"Got Characteristic : " + uuidStr);
                for (int ii = 0; ii < charList.size(); ii++) {
                    BluetoothGattCharacteristic tempC = charList.get(ii);
                    if ((tempC.getUuid().toString().equals(uuidStr))) {
                        for (int jj = 0; jj < mProfiles.size(); jj++) {
                            GenericBluetoothProfile p = mProfiles.get(jj);
                            if (p.isDataC(tempC)) {
                                p.didUpdateValueForCharacteristic(tempC);
                            }
                        }
                        //Log.d(TAG, "Got Characteristic : " + tempC.getUuid().toString());
                        break;
                    }
                }

                //onCharacteristicChanged(uuidStr, value);
            } else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
                // Data written
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                for (int ii = 0; ii < charList.size(); ii++) {
                    BluetoothGattCharacteristic tempC = charList.get(ii);
                    if ((tempC.getUuid().toString().equals(uuidStr))) {
                        for (int jj = 0; jj < mProfiles.size(); jj++) {
                            GenericBluetoothProfile p = mProfiles.get(jj);
                            p.didWriteValueForCharacteristic(tempC);
                        }
                        //Log.d(TAG,"Got Characteristic : " + tempC.getUuid().toString());
                        break;
                    }
                }
            } else if (BluetoothLeService.ACTION_DATA_READ.equals(action)) {
                // Data read
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                for (int ii = 0; ii < charList.size(); ii++) {
                    BluetoothGattCharacteristic tempC = charList.get(ii);
                    if ((tempC.getUuid().toString().equals(uuidStr))) {
                        for (int jj = 0; jj < mProfiles.size(); jj++) {
                            GenericBluetoothProfile p = mProfiles.get(jj);
                            p.didReadValueForCharacteristic(tempC);
                        }
                        //Log.d(TAG,"Got Characteristic : " + tempC.getUuid().toString());
                        break;
                    }
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                try {
                    Log.d(TAG, "Failed UUID was " + intent.getStringExtra(BluetoothLeService.EXTRA_UUID));
                    Log.e("TAG", "GATT error code: " + BluetoothGATTDefines.gattErrorCodeStrings.get(status));
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };
}
