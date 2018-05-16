package pt.ipleiria.doorsensorble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Created by Simao on 11/05/2018.
 */

public class DeviceProfile {
    public static UUID SERVICE_UUID = UUID.fromString("4fb39142-0ced-48bd-a746-7febb6d59237");

//    /** BATTERY SERVICE SO PARA TESTES*/
//    public static UUID SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    public static UUID CHARACTERISTIC_DOOR_UUID = UUID.fromString("91de3c1b-6cab-4eae-a108-8122464593f4");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static String getStateDescription(int state) {
        switch(state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "Connected";
            case BluetoothProfile.STATE_CONNECTING:
                return "Connecting";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "Disconnected";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "Disconnecting";
            default:
                return "Unknown State" + state;
        }
    }

    public static String getStatusDescription(int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS";
            default:
                return "Unknown Status " + status;
        }
    }

    public static int unsignedIntFromBytes(byte[] raw) {
        if (raw.length < 4) throw new IllegalArgumentException("Cannot convert raw data to int");

        return ((raw[0] & 0xFF)
                + ((raw[1] & 0xFF) << 8)
                + ((raw[2] & 0xFF) << 16)
                + ((raw[3] & 0xFF) << 24));
    }

    public static byte[] bytesFromInt(int value) {
        //Convert result into raw bytes. GATT APIs expect LE order
        return ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }

    public static byte[] generateData(int degree) {
//        int counter = 80000001;
        int counter = 125;

//        byte[] bCounter = new byte[] {
//            (byte)(counter >> 56),
//            (byte)(counter >> 48),
//            (byte)(counter >> 40),
//            (byte)(counter >> 32),
//            (byte)(counter >> 24),
//            (byte)(counter >> 16),
//            (byte)(counter >> 8),
//            (byte)counter
//        };

        byte[] bCounter = new byte[] {
            (byte)(counter >> 16),
            (byte)(counter >> 8),
            (byte)counter
        };

//        String uniqueId = "NWKA1O5O";
//        byte[] bUniqueID = uniqueId.getBytes();

        return ByteBuffer.allocate(7)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(bCounter)
                .putInt(degree)
//                .put(bUniqueID)
                .array();
    }
}
