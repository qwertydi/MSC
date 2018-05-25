package pt.ipleiria.doorsensorble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Simao on 11/05/2018.
 */

public class DeviceProfile {
    private static final String TAG = "DeviceProfile";

    public static UUID SERVICE_UUID = UUID.fromString("4fb39142-0ced-48bd-a746-7febb6d59237");

//    /** BATTERY SERVICE SO PARA TESTES*/
//    public static UUID SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    public static UUID CHARACTERISTIC_DOOR_UUID = UUID.fromString("91de3c1b-6cab-4eae-a108-8122464593f4");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final int MANUFACTURER_ID = 65534;
    public static int counter = 0;
    public static final String UNIQUE_ID = "NWKA1O5O";

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
//        int counter = 125;

        byte[] bCounter = new byte[] {
            (byte)(counter >> 56),
            (byte)(counter >> 48),
            (byte)(counter >> 40),
            (byte)(counter >> 32),
            (byte)(counter >> 24),
            (byte)(counter >> 16),
            (byte)(counter >> 8),
            (byte)counter
        };

        counter++;

        byte[] bUniqueID = UNIQUE_ID.getBytes();

        return ByteBuffer.allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(bCounter)
                .putInt(degree)
                .put(bUniqueID)
                .array();
    }

    public static byte[] encryptionTest(int degree) {
//        Log.i(TAG, "on encryptionTest");

        int mCounter = 58693214;
        int mDegree = -250;
//        String mUniqueID = "UNIQUEID";
//        String contatenatedString = mCounter + "" + mDegree + UNIQUE_ID;
        String contatenatedString = counter + "" + degree + UNIQUE_ID;
//        Log.i(TAG, "contatenatedString: " + contatenatedString);
//        Log.i(TAG, "contatenatedStringInBytesLength: " + contatenatedString.getBytes().length);

        //-----------------------------------------------------------

        SecretKeySpec sks = null;
        try {
            String key = "U027NCPWX7E4V68D";
            byte[] keyBytes = key.getBytes("ASCII");
            sks = new SecretKeySpec(keyBytes, "AES");

//            Log.i(TAG, "getKeySize: " + keyBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "AES secret key spec error");
        }

        byte[] ivBytes = null;
        try {
            String iv = "K1V3GJEVNTUF1AXT";
            ivBytes = iv.getBytes("ASCII");
//            Log.i(TAG, "getIVSize: " + ivBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "IV spec error");
        }



        // Encode the original data with AES
        byte[] encodedBytes = null;
        try {
            Cipher c = Cipher.getInstance("AES/CFB8/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, sks, new IvParameterSpec(ivBytes));
            encodedBytes = c.doFinal(contatenatedString.getBytes());
//            Log.i(TAG, "getAlgorithm: " + c.getAlgorithm());
//            Log.i(TAG, "getBlockSize: " + c.getBlockSize());
//            Log.i(TAG, "getIV: " + c.getIV());
//            Log.i(TAG, "getOutputSize(20): " + c.getOutputSize(contatenatedString.getBytes().length));
        } catch (Exception e) {
            Log.e(TAG, "AES encryption error:" + e);
        }

//        showByteArray(encodedBytes);

        // Decode the encoded data with AES
        byte[] decodedBytes = null;
        try {
            Cipher c = Cipher.getInstance("AES/CFB8/NoPadding");
            c.init(Cipher.DECRYPT_MODE, sks, new IvParameterSpec(ivBytes));
            decodedBytes = c.doFinal(encodedBytes);
        } catch (Exception e) {
            Log.e(TAG, "AES decryption error: " + e);
        }

        String str = new String(decodedBytes, Charset.forName("utf-8"));
//        Log.i(TAG, "decodedBytes: " + str);
        Log.i(TAG, "C: " + counter + " | Dº: " + degree + " | ID: " + UNIQUE_ID);
        Log.i(TAG, "E: " + contatenatedString);
        Log.i(TAG, "D: " + str);
        Log.i(TAG, "----------------------------------");

        return encodedBytes;
    }

    private static void showByteArray(byte[] bytesArray){
        ArrayList<Integer> mArray = new ArrayList<>(bytesArray.length);
        for (Byte b: bytesArray) {
            mArray.add(b.intValue());
        }
        Log.i(TAG, "showBytes: " + mArray + " -> " + mArray.size());
    }

    public byte[] applyEncryption(byte[] dataToEncrypt) {
        return ByteBuffer.allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .array();
    }

    public byte[] applyDecryption(byte[] encryptedData) {
        return ByteBuffer.allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .array();
    }
}
