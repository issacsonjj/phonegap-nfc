package com.chariotsolutions.nfc.plugin;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.ByteArrayOutputStream;

public class Util {

    static final String TAG = "NfcPlugin";

    static JSONObject ndefToJSON(Ndef ndef) {
        JSONObject json = new JSONObject();

        if (ndef != null) {
            try {

                Tag tag = ndef.getTag();
                // tag is going to be null for NDEF_FORMATABLE until NfcUtil.parseMessage is refactored
                if (tag != null) {
                    json.put("id", byteArrayToJSON(tag.getId()));
                    json.put("techTypes", new JSONArray(Arrays.asList(tag.getTechList())));
                }

                json.put("type", translateType(ndef.getType()));
                json.put("maxSize", ndef.getMaxSize());
                json.put("isWritable", ndef.isWritable());
                json.put("ndefMessage", messageToJSON(ndef.getCachedNdefMessage()));
                // Workaround for bug in ICS (Android 4.0 and 4.0.1) where
                // mTag.getTagService(); of the Ndef object sometimes returns null
                // see http://issues.mroland.at/index.php?do=details&task_id=47
                try {
                  json.put("canMakeReadOnly", ndef.canMakeReadOnly());
                } catch (NullPointerException e) {
                  json.put("canMakeReadOnly", JSONObject.NULL);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to convert ndef into json: " + ndef.toString(), e);
            }
        }
        return json;
    }

    public static byte charToByte(char c) {
		  return (byte) "0123456789ABCDEF".indexOf(c);
	  }

    public static String bytesToHexString(byte[] bytes) {
		  String result = "";

		  for (int i = 0;i < bytes.length;i++) {
			  String tmp = String.format("%02X", ((int)bytes[i]) & 0xff);
			  result += tmp;
		  }

		  return result;
	  }

    public static byte[] hexStringToBytes(String hexString) {
      if (hexString == null || hexString.equals("")) {
          return null;
      }
      hexString = hexString.toUpperCase();
      int length = hexString.length() / 2;
      char[] hexChars = hexString.toCharArray();
      byte[] d = new byte[length];
      for (int i = 0; i < length; i++) {
          int pos = i * 2;
          d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));

      }
      return d;
    }

    static byte[] intToBytes(int number, int length) {
      byte[] result = new byte[length];

      for (int i = 0;i < length;i++) {
        byte b = (byte) ((number >> i*8) & 0xff);
        result[length-i-1] = b;
      }

      return result;
    }

    static byte[] joinBytes(byte[] b1, byte[] b2) {
      ByteArrayOutputStream bi = new ByteArrayOutputStream();

      try {
        bi.write(b1);
        bi.write(b2);
      } catch(Exception e) {

      }
      return bi.toByteArray();
    }

    static JSONObject tagToJSON(Tag tag) {
        JSONObject json = new JSONObject();
        IsoDep isoDep = null;

        if (tag != null) {
            try {
                json.put("id", byteArrayToJSON(tag.getId()));
                json.put("techTypes", new JSONArray(Arrays.asList(tag.getTechList())));

                //read tag data

                String cmd1 = "00A404000A42554C414F5A48454E47";
              	String cmd2 = "00b0820000";
                isoDep = IsoDep.get(tag);
          			isoDep.connect();
                byte[] result1 = isoDep.transceive(hexStringToBytes(cmd1));
                String resultStr = bytesToHexString(result1);

                if (result1 != null && "9000".equals(resultStr.substring(resultStr.length() - 4))) {
          				byte[] result2 = isoDep.transceive(hexStringToBytes("00a40000020003"));
          				resultStr = bytesToHexString(result2);
          				if (result2 != null && "9000".equals(resultStr.substring(resultStr.length() - 4))) {
          					String tmpCmd = "00b0";
          					int length = 100;
                    ByteArrayOutputStream bi = new ByteArrayOutputStream();
          					for (int i = 0; i < 3; i++) {
          						int index = length * i;
          						byte[] bTmpCmd = hexStringToBytes(tmpCmd);
          						byte[] join1 = joinBytes(bTmpCmd, intToBytes(index, 2));
          						byte[] join2 = joinBytes(join1, intToBytes(length, 1));
          						byte[] result3 = isoDep.transceive(join2);
          						String resultStr1 = bytesToHexString(result3);
          						if ("9000".equals(resultStr1.substring(resultStr1.length() - 4))) {
          							bi.write(result3, 4, result3.length-4);
                        //showContent.append(resultStr1.substring(0,resultStr1.length() - 4));
          						}
          					}

                    json.put("data", byteArrayToJSON(bi.toByteArray()));
                  }

          			}

            } catch (Exception e) {
                Log.e(TAG, "Exception: " + e);
            }
        }
        return json;
    }

    static String translateType(String type) {
        String translation;
        if (type.equals(Ndef.NFC_FORUM_TYPE_1)) {
            translation = "NFC Forum Type 1";
        } else if (type.equals(Ndef.NFC_FORUM_TYPE_2)) {
            translation = "NFC Forum Type 2";
        } else if (type.equals(Ndef.NFC_FORUM_TYPE_3)) {
            translation = "NFC Forum Type 3";
        } else if (type.equals(Ndef.NFC_FORUM_TYPE_4)) {
            translation = "NFC Forum Type 4";
        } else {
            translation = type;
        }
        return translation;
    }

    static NdefRecord[] jsonToNdefRecords(String ndefMessageAsJSON) throws JSONException {
        JSONArray jsonRecords = new JSONArray(ndefMessageAsJSON);
        NdefRecord[] records = new NdefRecord[jsonRecords.length()];
        for (int i = 0; i < jsonRecords.length(); i++) {
            JSONObject record = jsonRecords.getJSONObject(i);
            byte tnf = (byte) record.getInt("tnf");
            byte[] type = jsonToByteArray(record.getJSONArray("type"));
            byte[] id = jsonToByteArray(record.getJSONArray("id"));
            byte[] payload = jsonToByteArray(record.getJSONArray("payload"));
            records[i] = new NdefRecord(tnf, type, id, payload);
        }
        return records;
    }

    static JSONArray byteArrayToJSON(byte[] bytes) {
        JSONArray json = new JSONArray();
        for (byte aByte : bytes) {
            json.put(aByte);
        }
        return json;
    }

    static byte[] jsonToByteArray(JSONArray json) throws JSONException {
        byte[] b = new byte[json.length()];
        for (int i = 0; i < json.length(); i++) {
            b[i] = (byte) json.getInt(i);
        }
        return b;
    }

    static JSONArray messageToJSON(NdefMessage message) {
        if (message == null) {
            return null;
        }

        List<JSONObject> list = new ArrayList<JSONObject>();

        for (NdefRecord ndefRecord : message.getRecords()) {
            list.add(recordToJSON(ndefRecord));
        }

        return new JSONArray(list);
    }

    static JSONObject recordToJSON(NdefRecord record) {
        JSONObject json = new JSONObject();
        try {
            json.put("tnf", record.getTnf());
            json.put("type", byteArrayToJSON(record.getType()));
            json.put("id", byteArrayToJSON(record.getId()));
            json.put("payload", byteArrayToJSON(record.getPayload()));
        } catch (JSONException e) {
            //Not sure why this would happen, documentation is unclear.
            Log.e(TAG, "Failed to convert ndef record into json: " + record.toString(), e);
        }
        return json;
    }

}
