package utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class HashUtils {
    /**
     * Generates a 40-character SHA-1 hash (checksum) for any given byte array.
     * This hash acts as the unique ID and filename for the object in Git's key-value datastore.
     */
    public static String generateHexString(byte[] blob) {
        if(blob == null) {
            throw new NullPointerException("blob is null. error creating blob");
        }
        String hexString = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashedBlob = digest.digest(blob);
            hexString = HexFormat.of().formatHex(hashedBlob);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
        }
        return hexString;
    }
}
