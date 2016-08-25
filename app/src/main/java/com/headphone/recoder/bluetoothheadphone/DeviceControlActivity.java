package com.headphone.recoder.bluetoothheadphone;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;

/**
 * 管理设备和显示数据的Activity
 * <p>
 * Created by zhangshan on 16/8/25 上午10:43.
 */
public class DeviceControlActivity extends Activity {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private String mDeviceName;
    private String mDeviceAddress;

    private TextView mConnectionState;
    private TextView mBattery;
    private TextView mBleBattery;
    private TextView mManufacturer;
    private TextView mStepCount;
    private TextView mBodyWeight;
    private TextView mShrink;
    private TextView mDiastole;
    private TextView mHeartRate;
    private TextView mRange;
    //private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private MyThread mt = null;
    private double weight;
    private boolean finish = true;

    private boolean mConnected = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.gatt_services_characteristics);

        Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mBattery = (TextView) findViewById(R.id.battery);
        mBleBattery = (TextView) findViewById(R.id.ble_battery);
        mManufacturer = (TextView) findViewById(R.id.manufacturer);
        mStepCount = (TextView) findViewById(R.id.step_count);
        mBodyWeight = (TextView) findViewById(R.id.body_weight);
        mShrink = (TextView) findViewById(R.id.shrink);
        mDiastole = (TextView) findViewById(R.id.diastole);
        mHeartRate = (TextView) findViewById(R.id.heart_rate);
        mRange = (TextView) findViewById(R.id.range);

        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, conn, BIND_AUTO_CREATE);

    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    String battery = msg.getData().getString("battery");
                    if (battery != null) {
                        Log.d("demo", String.valueOf(Integer.parseInt(battery, 16)));
                        int level = Integer.parseInt(battery, 16);
                        mBleBattery.setText(level + "%");
                    }
                    break;
                case 2:
                    String manufacturer = msg.getData().getString("manufacturer");
                    if (manufacturer != null) {
                        mManufacturer.setText(manufacturer);
                    }
                    break;
                case 3:
                    String data = msg.getData().getString("count");
                    String count = data.substring(4, 10);
                    int stepCount = Integer.parseInt(count, 16);
                    mStepCount.setText(stepCount + "");
                    break;
                case 4:
                    String body_weight = msg.getData().getString("bodyweight");
                    String bw = body_weight.substring(4, 8);
                    getWeight(bw);
                    break;
                case 5:
                    Bundle b = msg.getData();
                    mBodyWeight.setText(b.getDouble("weight") + " kg");
                    if (weight == 0.0) {
                        mHandler.removeCallbacks(mt);
                        mt = null;
                    }
                    break;
                case 6:
                    String blood = msg.getData().getString("bloodpressure");
                    getBloodPressure(blood);
                    break;
                default:
                    break;
            }
        }
    };

    public void getBloodPressure(String data) {
        String sk = data.substring(20, 24);
        int shrink = Integer.parseInt(sk, 16);
        mShrink.setText(shrink + " mmHg");
        String dia = data.substring(24, 26);
        int diastole = Integer.parseInt(dia, 16);
        mDiastole.setText(diastole + " mmHg");
        String hr = data.substring(26, 28);
        int heart = Integer.parseInt(hr, 16);
        mHeartRate.setText(heart + " BPM");
        getRange(shrink, diastole);
    }

    public void getRange(int shrink, int diastole) {
        if ((shrink >= 90 && shrink < 140) && (diastole >= 60 && diastole < 90)) {
            mRange.setText(R.string.normal);
        } else if (shrink > 139 || diastole > 90) {
            mRange.setText(R.string.high);
        } else if (shrink < 90 || diastole < 60) {
            mRange.setText(R.string.low);
        } else if (shrink > 139 && diastole < 90) {
            mRange.setText(R.string.isolated_systolic_hypertension);
        }
    }


    public void getWeight(String s) {
        int a = Integer.parseInt(s, 16);
        if (mt == null) {
            mt = new MyThread();
            mt.start();
        }
        double w = a;
        weight = w / 10;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(conn);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_service, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBind) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBluetoothLeService = null;
        }
    };


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Bundle bundle = new Bundle();
            Message msg = new Message();
            //Log.d("demo", " action : " + action);
            String mExtraData = BluetoothLeService.EXTRA_DATA;
            Log.d("bro_demo", " action : " + action + "---mExtraData : " + mExtraData);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVER.equals(action)) {
                mBluetoothLeService.readBattery();
            } else if (BluetoothLeService.ACTION_DATA_BLOODPRESSURE.equals(action)) {
                //displayBloodPressure(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                bundle.putString("bloodpressure", intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                msg.setData(bundle);
                msg.what = 6;
                mHandler.sendMessage(msg);
            } else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int level = intent.getIntExtra("level", 0);//获取当前电量
                int scale = intent.getIntExtra("scale", 100);//电量的总刻度
                mBattery.setText(((level * 100) / scale) + "%");//把它转成百分比
            } else if (BluetoothLeService.ACTION_DATA_BATTERY.equals(action)) {
                //displayBleBattery(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                bundle.putString("battery", intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                msg.setData(bundle);
                msg.what = 1;
                mHandler.sendMessage(msg);
            } else if (BluetoothLeService.ACTION_DATA_MENUFACTURER.equals(action)) {
                //displayManufacturer(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                bundle.putString("manufacturer", intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                msg.setData(bundle);
                msg.what = 2;
                mHandler.sendMessage(msg);
            } else if (BluetoothLeService.ACTION_DATA_COUNT.equals(action)) {
                //displayCount(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                bundle.putString("count", intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                msg.setData(bundle);
                msg.what = 3;
                mHandler.sendMessage(msg);
            } else if (BluetoothLeService.ACTION_DATA_BODYWEIGHT.equals(action)) {
                //displayBodyWeight(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                bundle.putString("bodyweight", intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                msg.setData(bundle);
                msg.what = 4;
                mHandler.sendMessage(msg);
            }
        }
    };

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    /*private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
    }*/



/*    private void displayBloodPressure(String data) {
        if (data != null) {
            mBloodPressure.setText(data);
        }
    }

    private void displayBodyWeight(String data) {
        if (data != null) {
            mBodyWeight.setText(data);
        }
    }*/

/*    public void displayBleBattery(String data){
        Log.d("demo", "battery data != null --->"+String.valueOf(data!=null));
        if(data != null){
            Log.d("demo", String.valueOf(Integer.parseInt(data,16)));
            int level = Integer.parseInt(data, 16);
            mBleBattery.setText(level+"%");
        }
    }*/


/*    public void displayManufacturer(String data) {
        if (data != null) {
            mManufacturer.setText(data);
        }
    }


    public void displayCount(String data) {
        if (data != null) {
            String count = data.substring(4, 10);
            Log.d("demo", count);
            Log.d("demo", String.valueOf(Integer.parseInt(count, 16)));
            int stepCount = Integer.parseInt(count, 16);
            mStepCount.setText(stepCount + "");
        }
    }*/

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVER);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_BLOODPRESSURE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_BODYWEIGHT);
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_MENUFACTURER);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_BATTERY);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_COUNT);
        return intentFilter;
    }

    class MyThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (finish) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Message msg = new Message();
                Bundle bundle = new Bundle();
                bundle.putDouble("weight", weight);
                msg.setData(bundle);
                msg.what = 5;
                mHandler.sendMessage(msg);
            }
        }

    }
}
