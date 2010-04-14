/**
 * OnionCoffee - Anonymous Communication through TOR Network Copyright (C)
 * 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package net.sf.onioncoffee.common;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

/**
 * this class contains utility functions concerning encodings
 * 
 * @author Brad Davis
 * @author Lexi Pimenidis
 * @author Andriy Panchenko
 * @author Michael Koellejan
 */
public class Encoding {
    static String[] hexChars = { "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "0a", "0b", "0c", "0d", "0e", "0f", "10", "11", "12", "13", "14", "15", "16", "17",
            "18", "19", "1a", "1b", "1c", "1d", "1e", "1f", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "2a", "2b", "2c", "2d", "2e", "2f", "30", "31", "32", "33",
            "34", "35", "36", "37", "38", "39", "3a", "3b", "3c", "3d", "3e", "3f", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "4a", "4b", "4c", "4d", "4e", "4f",
            "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "5a", "5b", "5c", "5d", "5e", "5f", "60", "61", "62", "63", "64", "65", "66", "67", "68", "69", "6a", "6b",
            "6c", "6d", "6e", "6f", "70", "71", "72", "73", "74", "75", "76", "77", "78", "79", "7a", "7b", "7c", "7d", "7e", "7f", "80", "81", "82", "83", "84", "85", "86", "87",
            "88", "89", "8a", "8b", "8c", "8d", "8e", "8f", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "9a", "9b", "9c", "9d", "9e", "9f", "a0", "a1", "a2", "a3",
            "a4", "a5", "a6", "a7", "a8", "a9", "aa", "ab", "ac", "ad", "ae", "af", "b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "ba", "bb", "bc", "bd", "be", "bf",
            "c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "ca", "cb", "cc", "cd", "ce", "cf", "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "da", "db",
            "dc", "dd", "de", "df", "e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "ea", "eb", "ec", "ed", "ee", "ef", "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7",
            "f8", "f9", "fa", "fb", "fc", "fd", "fe", "ff" };

    /**
     * Converts a byte array to hex string
     */
    public static String toHexString(byte[] block, int column_width, int offset, int length) {
        byte[] temp = new byte[length];
        System.arraycopy(block, offset, temp, 0, length);
        return toHexString(temp, column_width);
    }

    /**
     * Converts a byte array to hex string
     */
    public static String toHexString(byte[] block, int column_width) {
        StringBuffer buf = new StringBuffer(4 * (block.length + 2));
        for (int i = 0; i < block.length; i++) {
            if (i > 0) {
                buf.append(":");
                if (i % (column_width / 3) == 0) {
                    buf.append("\n");
                }
            }
            buf.append(hexChars[block[i] & 0xff]);
        }
        return buf.toString();
    }

    /**
     * Converts a byte array to hex string
     */
    public static String toHexStringNoColon(byte[] block) {
        StringBuffer buf = new StringBuffer(4 * (block.length + 2));
        for (int i = 0; i < block.length; i++) {
            buf.append(hexChars[block[i] & 0xff]);
        }
        return buf.toString();
    }

    public static String toHexString(byte[] block) {
        return toHexString(block, block.length * 3 + 1);
    }

    /**
     * Convert int to the array of bytes
     * 
     * @param myInt
     *            integer to convert
     * @param n
     *            size of the byte array
     * @return byte array of size n
     * 
     */
    public static byte[] intToNByteArray(int myInt, int n) {

        byte[] myBytes = new byte[n];

        for (int i = 0; i < n; ++i) {
            myBytes[i] = (byte) ((myInt >> ((n - i - 1) * 8)) & 0xff);
        }
        return myBytes;
    }

    /**
     * wrapper to convert int to the array of 2 bytes
     * 
     * @param myInt
     *            integer to convert
     * @return byte array of size two
     */
    public static byte[] intTo2ByteArray(int myInt) {
        return intToNByteArray(myInt, 2);
    }

    /**
     * Convert the byte array to an int starting from the given offset.
     * 
     * @param b
     *            byte array
     * @param offset
     *            array offset
     * @param length
     *            number of bytes to convert
     * @return integer
     */
    public static int byteArrayToInt(byte[] b, int offset, int length) {
        int value = 0;
        int numbersToConvert = b.length - offset;

        // 4 bytes is max int size (2^32)
        int n = Math.min(Math.min(length, 4), numbersToConvert);

        // if (numbersToConvert > 4)
        // offset = b.length - 4; // warning: offset has been changed
        // in order to convert LSB
        for (int i = 0; i < n; i++) {
            int shift = (n - 1 - i) * 8;
            value += (b[i + offset] & 0xff) << shift;
        }
        return value;
    }

    /**
     * Convert the byte array to an int
     * 
     * @param b
     *            byte array
     * @return the integer
     * 
     */
    public static int byteArrayToInt(byte[] b) {
        return byteArrayToInt(b, 0, b.length);
    }

    public static boolean isDottedNotation(String s) {
        Pattern p = Pattern.compile("\\A(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)\\z");
        Matcher m = p.matcher(s);
        return m.find();
    }

    /**
     * converts a notation like 192.168.3.101 into a binary format
     * 
     * @param s
     *            a string containing the dotted notation
     * @return the binary format
     */
    public static long dottedNotationToBinary(String s) {
        long temp = 0;
        Pattern p = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)");
        Matcher m = p.matcher(s);
        if (m.find()) {
            for (int i = 1; i <= 4; ++i) {
                temp = temp << 8;
                temp = temp | Integer.parseInt(m.group(i));
            }
        }
        return temp;
    }

    /**
     * converts netmask into int - number of significant bits
     * 
     * @param netmask
     *            netmask
     * 
     * @return number of significant bits
     */
    public static int netmaskToInt(long netmask) {
        int result = 0;
        while ((netmask & 0xffffffffL) != 0) {
            netmask = netmask << 1;
            result++;
        }
        return result;
    }

    /**
     * converts our binary format back into dotted-decimal notation
     * 
     * @param ip
     *            binary encoded ip-address.
     */
    public static String binaryToDottedNotation(long ip) {
        StringBuffer dottedNotation = new StringBuffer();
        dottedNotation.append(((ip & 0xff000000) >> 24) + ".");
        dottedNotation.append(((ip & 0x00ff0000) >> 16) + ".");
        dottedNotation.append(((ip & 0x0000ff00) >> 8) + ".");
        dottedNotation.append(((ip & 0x000000ff) >> 0));
        return dottedNotation.toString();
    }

    /** creates an base64-string out of a byte[] */
    public static String toBase64(byte[] data) {
        return new String(Base64.encodeBase64(data), Charsets.UTF_8);
    }

    /** creates an base64-string out of a byte[] */
    public static String toFoldedBase64(byte[] data) {
        return toFoldedBase64(data, 64);
    }

    /** creates an base64-string out of a byte[] */
    public static String toFoldedBase64(byte[] data, int len) {
        String source = new String(Base64.encodeBase64(data));
        StringBuilder buffer = new StringBuilder();
        while (source.length() > len) {
            buffer.append(source.substring(0, len) + "\n");
            source = source.substring(len);
        }
        buffer.append(source + "\n");
        return buffer.toString();
    }

    private static final String[] ADDATIVES = { "", "===", "==", "=" };

    /**
     * parses a base64-String. <br>
     * <b>Q</b>: Why doesn't provide Java us with such a functionality? <br>
     * <b>A</b>: Because it sucks. <br>
     * <b>A</b>: RTFM e.g.
     * 
     * @param s
     *            a string that contains a base64 encoded array
     * @return the decoded array
     */
    public static byte[] parseBase64(String s) {
        return Base64.decodeBase64((s + ADDATIVES[s.length() % 4]).getBytes());
    }

    /**
     * parses a hex-string into a byte-array.<br>
     * <b>Q</b>: Why doesn't provide Java us with such a functionality? <br>
     * <b>A</b>: Because it sucks.
     * 
     * @param s
     *            a string that contains a hex-encoded array
     * @return the decoded array
     * @throws DecoderException
     */
    public static byte[] parseHex(String s) {
        try {
            return Hex.decodeHex(s.replaceAll("[^a-fA-F0-9]", "").toCharArray());
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * do a base32-enconding from a binary field
     */
    public static String toBase32(byte[] data) {
        String base32 = "abcdefghijklmnopqrstuvwxyz234567";

        StringBuffer sb = new StringBuffer();
        int b32 = 0;
        int b32_filled = 0;
        for (int pos = 0; pos < data.length; ++pos) {
            for (int bitmask = 128; bitmask > 0; bitmask /= 2) {
                b32 = (b32 << 1);
                if (((int) data[pos] & bitmask) != 0) {
                    b32 = b32 | 1;
                }
                ++b32_filled;
                if (b32_filled == 5) {
                    sb.append(base32.charAt(b32)); // transform to
                    // base32-encoding
                    b32 = 0;
                    b32_filled = 0;
                }
            }
        }
        // check if bits were left unencoded
        if (b32_filled != 0) {
            LoggerFactory.getLogger(Encoding.class).warn("Common.toBase32: received array with unsupported number of bits " + Encoding.toHexString(data));
        }
        // return result
        return sb.toString();
    }

    /**
     * makes hashmap with x,y,z parts of the hidden service address
     * 
     * @param hostname
     *            hostname of the hidden service
     * @return hashmap with keys x,y,z
     */
    public static Map<String, String> parseHiddenAddress(String hostname) {

        String x, y, z;
        HashMap<String, String> result = new HashMap<String, String>(3);

        z = hostname;
        z = z.replaceFirst(".onion", "");

        x = RegexUtil.parseStringByRE(z, "(.*?)\\.", "");
        z = z.replaceFirst(x + "\\.", "");

        y = RegexUtil.parseStringByRE(z, "(.*?)\\.", "");
        z = z.replaceFirst(y + "\\.", "");

        if (y == "") {
            y = x;
            x = "";
        }

        result.put("x", x);
        result.put("y", y);
        result.put("z", z);

        return result;

    }


    public static byte[] extractBase64Data(String s) {
        return Base64.decodeBase64(RegexUtil.parseStringByRE(s, "-----BEGIN .*?-----(.*?)-----END .*?-----", null).getBytes());
    }

    /**
     * helper function to convert a bigInteger to a fixed-sized array for
     * TOR-Usage
     */
    public static byte[] bigIntegerTo128Bytes(BigInteger a) {
        byte[] temp = a.toByteArray();
        byte[] result = new byte[128];
        Arrays.fill(result, (byte) 0);
        if (temp.length > 128) {
            System.arraycopy(temp, temp.length - 128, result, 0, 128);
        } else {
            System.arraycopy(temp, 0, result, 128 - temp.length, temp.length);
        }
        return result;
    }
}
