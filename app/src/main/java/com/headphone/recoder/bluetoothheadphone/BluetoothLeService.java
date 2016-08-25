package com.headphone.recoder.bluetoothheadphone;

import android.app.Service;
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
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * 服务类
 *
 * Created by zhangshan on 16/8/25 上午10:45.
 */
public class BluetoothLeService extends Service {
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private Handler mHandler = new Handler();

    private final static int STATE_DISCONNECTED = 0;
    private final static int STATE_CONNECTING = 1;
    private final static int STATE_CONNECTED = 2;

    private final static int RUN_PERIOD = 3000;
    private final static int COUNT_PERIOD = 3000;

    public final static String ACTION_GATT_CONNECTED = "com.example.chongyanghu.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.chongyanghu.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVER = "com.example.chongyanghu.ble.ACTION_GATT_SERVICES_DISCOVER";

    public final static String EXTRA_DATA = "com.example.chongyanghu.ble.EXTRA_DATA";
    public final static String ACTION_DATA_BATTERY = "com.example.chongyanghu.ble.ACTION_DATA_BATTERY";
    public final static String ACTION_DATA_MENUFACTURER = "com.example.chongyanghu.ble.ACTION_DATA_MENUFACTURER";
    public final static String ACTION_DATA_COUNT = "com.example.chongyanghu.ble.ACTION_DATA_COUNT";
    public final static String ACTION_DATA_BLOODPRESSURE = "com.example.chongyanghu.ble.ACTION_DATA_BLOODPRESSURE";
    public final static String ACTION_DATA_BODYWEIGHT = "com.example.chongyanghu.ble.ACTION_DATA_BODYWEIGHT";

    //public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    public final static String BATTERY_SERVICE = "0000180F-0000-1000-8000-00805f9b34fb";
    public final static String BATTERY_CHARATERISTIC = "00002a19-0000-1000-8000-00805f9b34fb";
    public final static String MANUFACTURER_SERVICE = "0000180a-0000-1000-8000-00805f9b34fb";
    public final static String MANUFACTURER_CHARATERISTIC = "00002a29-0000-1000-8000-00805f9b34fb";
    public final static String CUSTOM_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public final static String CUSTOM_CHARACTERISTIC = "0000ffe1-0000-1000-8000-00805f9b34fb";


    private final IBinder mBinder = new LocalBind();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBind extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }
        return true;
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }
        if (mBluetoothDeviceAddress != null && mBluetoothGatt != null
                && address.equals(mBluetoothDeviceAddress)) {
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallBack);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    private final BluetoothGattCallback mGattCallBack = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d("demo", "onCharacteristicChanged");
            byte[] data = characteristic.getValue();
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X", byteChar));
            String builder = stringBuilder.toString();
            Log.d("demo", "0x -->" + builder);
            if(data[0] == (byte)0xC1){
                broadcastUpdate(ACTION_DATA_COUNT, characteristic);
            }else if(data[0] == (byte)0xB1){
                broadcastUpdate(ACTION_DATA_BLOODPRESSURE, characteristic);
            }else if(data[0] == (byte)0xB7){
                broadcastUpdate(ACTION_DATA_BODYWEIGHT, characteristic);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVER);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            UUID uuid = descriptor.getCharacteristic().getUuid();

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("demo", "state = " + String.valueOf(status == BluetoothGatt.GATT_SUCCESS));
                if (characteristic.getUuid().toString().equalsIgnoreCase(BATTERY_CHARATERISTIC)) {
                    Log.d("demo", String.valueOf(characteristic.getValue()[0]));
                    broadcastUpdate(ACTION_DATA_BATTERY, characteristic);
                } else if (characteristic.getUuid().toString().equalsIgnoreCase(MANUFACTURER_CHARATERISTIC)) {
                    Log.d("demo", "readManufacturer");
                    broadcastUpdate(ACTION_DATA_MENUFACTURER, characteristic);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }
    };

    //打开设备的通知功能
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        Log.d("demo", "setCharacteristicNotification");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enable);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothGatt == null || mBluetoothAdapter == null) {
            return;
        }
        Log.d("demo", "readCharacteristic");
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    private void broadcastUpdate(String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(String action, BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        String uuid = characteristic.getUuid().toString();
        Log.d("demo", "into broadcastUpdate");
        if (uuid.equalsIgnoreCase(BATTERY_CHARATERISTIC)) {
            byte[] databattery = characteristic.getValue();
            analysisData(intent, databattery);
        } else if (uuid.equalsIgnoreCase(MANUFACTURER_CHARATERISTIC)) {
            final byte[] datamanufacturer = characteristic.getValue();
            intent.putExtra(EXTRA_DATA,new String(datamanufacturer));
            sendBroadcast(intent);
        } else if (uuid.equalsIgnoreCase(CUSTOM_CHARACTERISTIC)) {
            byte[] data = characteristic.getValue();
            analysisData(intent,data);
        }
    }


    public void analysisData(Intent intent, byte[] data) {
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            Log.d("demo", String.valueOf(data.length));
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X", byteChar));
            String builder = stringBuilder.toString();
            Log.d("demo", "0x -->" + builder);
            intent.putExtra(EXTRA_DATA,builder);
            sendBroadcast(intent);
        }
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }

    public void readBattery() {
        Log.d("demo", "readBattery");
        BluetoothGattService batteryService = mBluetoothGatt.getService(UUID.fromString(BATTERY_SERVICE));
        if (batteryService != null) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Handler mHandler = new Handler();
                    BluetoothGattService manufacturerService = mBluetoothGatt.getService(UUID.fromString(MANUFACTURER_SERVICE));
                    if (manufacturerService != null) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                BluetoothGattService customService = mBluetoothGatt.getService(UUID.fromString(CUSTOM_SERVICE));
                                if (customService != null) {
                                    BluetoothGattCharacteristic customCharacteristic = customService.getCharacteristic(UUID.fromString(CUSTOM_CHARACTERISTIC));
                                    if (customCharacteristic != null) {
                                        final int charaProp = customCharacteristic.getProperties();
                                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                            setCharacteristicNotification(customCharacteristic, true);
                                        }
                                    }
                                }
                            }
                        }, COUNT_PERIOD);
                        BluetoothGattCharacteristic manufacturerCharacteristic = manufacturerService.getCharacteristic(UUID.fromString(MANUFACTURER_CHARATERISTIC));
                        Log.d("demo", "manufacturerCharacteristic = " + String.valueOf(manufacturerCharacteristic != null));
                        final int charaProp = manufacturerCharacteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            Log.d("demo", "intoNotify--->" + String.valueOf((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0));
                            readCharacteristic(manufacturerCharacteristic);
                        }
                    }
                }
            }, RUN_PERIOD);
            BluetoothGattCharacteristic batteryCharacteristic = batteryService.getCharacteristic(UUID.fromString(BATTERY_CHARATERISTIC));
            if (batteryCharacteristic != null) {
                readCharacteristic(batteryCharacteristic);
            }
        }
    }
}
