package pt.ipleiria.doorsensorble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class MainActivity extends Activity {

    //TODO fazer isto estar a fazer advertise de x em x tempo com durançao de y secs

    private static final String TAG = "DoorMainActivity";

    SensorManager sensorManager;
    Sensor rotationVectorSensor;
    SensorEventListener rListener;

    TextView textViewDoorDegrees;
    TextView textViewDevicesConnected;
    ListView listViewDevicesConnected;
    Button buttonDegreeReposition;

    int degree = 0;
    int degreeReposition = 0;
    int degreeSimplified = 0;
    int lastNotifiedDegreeValue = 0;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattServer mGattServer;
    private ArrayList<BluetoothDevice> mConnectedDevices;
    private ArrayAdapter<BluetoothDevice> mConnectedDevicesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(rotationVectorSensor == null) {
            Log.e(TAG, "Sensor not available.");
            finish(); // Close app
        }


        mConnectedDevices = new ArrayList<BluetoothDevice>();
        mConnectedDevicesAdapter = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_1, mConnectedDevices);
        textViewDoorDegrees = findViewById(R.id.textViewDegrees);
        textViewDevicesConnected = findViewById(R.id.textViewDevicesConnected);
        textViewDevicesConnected .setText("Connected Devices: " + String.valueOf(mConnectedDevices.size()));
        buttonDegreeReposition = findViewById(R.id.buttonDegreeReposition);
        buttonDegreeReposition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                degreeReposition = degree * -1;
            }
        });

        /*
         * Bluetooth in Android 4.3+ is accessed via the BluetoothManager, rather than
         * the old static BluetoothAdapter.getInstance()
         */
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        listViewDevicesConnected = findViewById(R.id.listViewConnectedDevices);
        listViewDevicesConnected.setAdapter(mConnectedDevicesAdapter);

    }

    @Override
    protected void onResume() {
        super.onResume();

        rListener = new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float[] rotationMatrix = new float[16];
                float[] orientationAngles  = new float[3];
                long[] orientations  = new long[3];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values);

                float[] remappedRotationMatrix = new float[16];
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedRotationMatrix);

                SensorManager.getOrientation(remappedRotationMatrix, orientationAngles);

                for(int i = 0; i < 3; i++) {
                    orientations[i] = Math.round(Math.toDegrees(orientationAngles[i]));
                }

                String ss = "|";
                for(long i : orientations) {
                    ss += i + "|";
                }
                textViewDoorDegrees.setText(ss);

                int m_degree = (int)Math.round(Math.toDegrees(orientationAngles[0]));

                degree = m_degree;
                degreeSimplified = degree + degreeReposition;
                textViewDoorDegrees.setText(degreeSimplified + "º");

                //TODO notify aqui
                if (Math.abs(degreeSimplified) < 200) {
                    if (Math.abs(Math.abs(lastNotifiedDegreeValue) - Math.abs(degreeSimplified)) >= 5) {
                        BluetoothGattCharacteristic mCharacteristic = mGattServer.getService(DeviceProfile.SERVICE_UUID).getCharacteristic(DeviceProfile.CHARACTERISTIC_DOOR_UUID);
                        mCharacteristic.setValue(DeviceProfile.bytesFromInt(Math.abs(degreeSimplified)));
                        notifyConnectedDevices();
                        lastNotifiedDegreeValue = degreeSimplified;
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        sensorManager.registerListener(rListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);

        //------------------------------------------------------------------------------------------

        /*
         * We need to enforce that Bluetooth is first enabled, and take the
         * user to advertiseSettings to enable it if they have not done so.
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry will keep this
         * from installing on these devices, but this will allow test devices or other
         * sideloads to report whether or not the feature exists.
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        /*
         * Check for advertising support. Not all devices are enabled to advertise
         * Bluetooth LE data.
         */
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, "No Advertising Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);

        initServer();
        startAdvertising();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(rListener);

        stopAdvertising();
        shutdownServer();
        mConnectedDevices.clear();
    }

    /*
 * Create the GATT server instance, attaching all services and
 * characteristics that should be exposed
 */
    private void initServer() {
        BluetoothGattService service =new BluetoothGattService(DeviceProfile.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic batteryLevelCharacteristic = new BluetoothGattCharacteristic(DeviceProfile.CHARACTERISTIC_DOOR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY/* | BluetoothGattCharacteristic.PROPERTY_BROADCAST*/,
                BluetoothGattCharacteristic.PERMISSION_READ);

        batteryLevelCharacteristic.setValue(DeviceProfile.bytesFromInt(degreeSimplified));

        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                DeviceProfile.CLIENT_CHARACTERISTIC_CONFIGURATION_UUID,
                (BluetoothGattDescriptor.PERMISSION_WRITE));

        /** Ao ativar o notify o valor passa para {1, 0}*/
        descriptor.setValue(new byte[]{0, 0});

        batteryLevelCharacteristic.addDescriptor(descriptor);
        service.addCharacteristic(batteryLevelCharacteristic);
        mGattServer.addService(service);
    }

    /*
     * Terminate the server and any running callbacks
     */
    private void shutdownServer() {
        mHandler.removeCallbacks(mNotifyRunnable);

        if (mGattServer == null) return;

        mGattServer.close();
    }

    private Runnable mNotifyRunnable = new Runnable() {
        @Override
        public void run() {
            /** TODO lidar depois com isto */
//            notifyConnectedDevices();
            /** podemos indicar aqui o timing para os notifies, 1 segundos de momento*/
            mHandler.postDelayed(this, 1000);
        }
    };

    /*
     * Callback handles all incoming requests from GATT clients.
     * From connections to read/write requests.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.i(TAG, "onConnectionStateChange "
                    +DeviceProfile.getStatusDescription(status)+" "
                    +DeviceProfile.getStateDescription(newState));

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                postDeviceChange(device, true);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                postDeviceChange(device, false);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(TAG, "onCharacteristicReadRequest " + characteristic.getUuid().toString() + " " + degreeSimplified);

            if (DeviceProfile.CHARACTERISTIC_DOOR_UUID.equals(characteristic.getUuid())) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        DeviceProfile.bytesFromInt(degreeSimplified)
                );
            }

            /*
             * Unless the characteristic supports WRITE_NO_RESPONSE,
             * always send a response back for any request.
             */
            mGattServer.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.i(TAG, "onDescriptorWriteRequest " + descriptor.getUuid().toString() + " " + Arrays.toString(value));

            //TODO faltam muitas validaçoes aqui, abordo isto como o caminho sem problemas

            int status = BluetoothGatt.GATT_SUCCESS;
            descriptor.setValue(value);

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, status,0,null);
            }

        }
    };


    /*
     * Initialize the advertiser
     */
    private void startAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true) //se fosse false, passava a ser beacon, ou seja, não pode ser connectado
//                .setTimeout(5000)
                .build();

        ParcelUuid pUuid = new ParcelUuid(DeviceProfile.SERVICE_UUID);
//        ParcelUuid pUuid = new ParcelUuid(uuid);
//        byte [] data = DeviceProfile.generateData(degreeSimplified);
        byte [] data = DeviceProfile.generateData(56);

        AdvertiseData advertiseData = new AdvertiseData.Builder()
//                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
//                .addServiceUuid(pUuid)
                .addServiceData(pUuid, data)
                .build();

        Log.i(TAG, "Peripheral startAdvertising-advertiseData: " + advertiseData);

        AdvertiseData advertiseScanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
//                .addServiceUuid(pUuid)
//                .addServiceData(pUuid, data)
                .build();

        Log.i(TAG, "Peripheral startAdvertising-advertiseScanResponse: " + advertiseScanResponse);

        mBluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseScanResponse, mAdvertiseCallback);
    }

    /*
     * Terminate the advertiser
     */
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /*
     * Callback handles events from the framework describing
     * if we were successful in starting the advertisement requests.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "Peripheral Advertise Started.");
            Log.i(TAG, "Peripheral Advertise settingsInEffect: " + settingsInEffect);
//            postStatusMessage("GATT Server Ready");
        }

        @Override
        public void onStartFailure(int errorCode) {

            String errorMessage = "UNKNOWN_ERROR";

            switch(errorCode) {
                case 1: errorMessage = "ADVERTISE_FAILED_DATA_TOO_LARGE"; break;
                case 2: errorMessage = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS"; break;
                case 3: errorMessage = "ADVERTISE_FAILED_ALREADY_STARTED"; break;
                case 4: errorMessage = "ADVERTISE_FAILED_INTERNAL_ERROR"; break;
                case 5: errorMessage = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED"; break;
            }

            Log.w(TAG, "Peripheral Advertise Failed: "+errorMessage);
//            postStatusMessage("GATT Server Error "+errorCode);
        }
    };

    private void postDeviceChange(final BluetoothDevice device, final boolean toAdd) {
        if (toAdd) {
            mConnectedDevices.add(device);
        } else {
            mConnectedDevices.remove(device);
        }

        //Trigger our periodic notification once devices are connected
        mHandler.removeCallbacks(mNotifyRunnable);
        if (!mConnectedDevices.isEmpty()) {
            mHandler.post(mNotifyRunnable);
        }

        textViewDevicesConnected.setText("Connected Devices: " + String.valueOf(mConnectedDevices.size()));
    }

    private Handler mHandler = new Handler();

    private void notifyConnectedDevices() {
        //TODO isto tem um problema para o uso de notify em varios devices, ou guardo a lista para os quais faço notify ou cenas...
        Log.i(TAG, "notifyConnectedDevices #" + mConnectedDevices.size());
        BluetoothGattCharacteristic doorCharacteristic = mGattServer.getService(DeviceProfile.SERVICE_UUID).getCharacteristic(DeviceProfile.CHARACTERISTIC_DOOR_UUID);

        boolean indicate = (doorCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE)
                == BluetoothGattCharacteristic.PROPERTY_INDICATE;

        boolean canNotify = Arrays.toString(doorCharacteristic.getDescriptor(DeviceProfile.CLIENT_CHARACTERISTIC_CONFIGURATION_UUID).getValue()).charAt(1) == '1';

        if (!canNotify) {
            Log.i(TAG, "Not notifying");
        } else {
            for (BluetoothDevice device : mConnectedDevices) {
                boolean sentStatus = mGattServer.notifyCharacteristicChanged(device, doorCharacteristic, indicate);
                Log.i(TAG, "notifyConnectedDevices " + device + " " + doorCharacteristic.getUuid() + " " + sentStatus);
            }
        }
    }
}
