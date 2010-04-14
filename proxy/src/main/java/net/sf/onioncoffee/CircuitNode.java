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
package net.sf.onioncoffee;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

import net.sf.onioncoffee.common.AESCounterMode;
import net.sf.onioncoffee.common.Encryption;
import net.sf.onioncoffee.common.TorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * represents a server as part of a specific circuit. Stores the additional data
 * and contains all of the complete crypto-routines.
 * 
 * @author Brad Davis
 * @author Lexi Pimenidis
 * @author Tobias Koelsch
 */
public class CircuitNode  {
    // The SKIP 1024 bit modulus
    static final BigInteger DH_P = new BigInteger("00FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08" + //
            "8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B" + //
            "302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9" + //
            "A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6" + //
            "49286651ECE65381FFFFFFFFFFFFFFFF", 16);

    // The base used with the SKIP 1024 bit modulus
    static final BigInteger DH_G = new BigInteger("2");

    Server server;
    private final AESCounterMode encryptStreamCipher;
    private final AESCounterMode decryptStreamCipher;
    private final MessageDigest forwardDigest;
    private final MessageDigest backwardDigest;

    /**
     * constructor for client-side
     * 
     * called after receiving created or extended cell: finished DH-key
     * exchange. Expects the first 148 bytes of the data array to be filled
     * with:<br>
     * <ul>
     * <li>128 bytes of DH-data (g^y)
     * <li>20 bytes of derivated key data (KH) (see chapter 4.2 of torspec)
     * </ul>
     * 
     * @param data
     *            expects the received second half of the DH-key exchange
     * @throws TorException
     * 
     * 
     */
    CircuitNode(Server init, byte[] data, BigInteger dh_private) throws TorException {
        // save a pointer to the server's data
        this.server = init;
        // calculate g^xy
        // - fix some undocument stuff: all numbers are 128-bytes only!
        // - add a leading zero to all numbers
        final byte[] dh_y_bytes = new byte[128];
        System.arraycopy(data, 0, dh_y_bytes, 0, 128);
        BigInteger dh_y = new BigInteger(1, dh_y_bytes);
        BigInteger dh_xy = dh_y.modPow(dh_private, DH_P);
        final byte[] dh_xy_bytes = BigIntegerTo128Bytes(dh_xy);

        // derive key material
        try {
            byte[] k = new byte[100];
            byte[] sha1_input = new byte[dh_xy_bytes.length + 1];
            System.arraycopy(dh_xy_bytes, 0, sha1_input, 0, dh_xy_bytes.length);

            MessageDigest sha1 = MessageDigest.getInstance(Encryption.HASH_ALGORITHM);
            for (int i = 0; i < 5; ++i) {
                sha1.reset();
                sha1_input[sha1_input.length - 1] = (byte) i;
                sha1.update(sha1_input);
                sha1.digest(k, i * 20, 20);
            }

            // check if derived key data is equal to bytes 128-147 of data[]
            // is there some error in the key data?
            if (!Arrays.equals(Arrays.copyOfRange(k, 0, 20), Arrays.copyOfRange(data, 128, 128 + 20))) {
                throw new TorException("derived key material is wrong!");
            }

            final byte[] kh = new byte[20]; // the derived key data
            final byte[] kf = new byte[16]; // symmetric key for sending data
            final byte[] kb = new byte[16]; // symmetric key for receiving data

            // derived key info is correct - save to final destination handshake
            System.arraycopy(k, 0, kh, 0, 20);
            // secret key for sending data
            System.arraycopy(k, 60, kf, 0, 16);
            // secret key for receiving data
            System.arraycopy(k, 76, kb, 0, 16);

            forwardDigest = MessageDigest.getInstance(Encryption.HASH_ALGORITHM);
            forwardDigest.update(k, 20, 20);
            backwardDigest = MessageDigest.getInstance(Encryption.HASH_ALGORITHM);
            backwardDigest.update(k, 40, 20);
            decryptStreamCipher = new AESCounterMode(true, kb);
            encryptStreamCipher = new AESCounterMode(true, kf);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    protected Logger getLog() {
        return LoggerFactory.getLogger(getClass());
    }

    /**
     * calculate the forward digest
     * 
     * @param data
     * @return a four-byte array containing the digest
     */
    public byte[] calcForwardDigest(byte[] data) {
        try {
            forwardDigest.update(data, 0, data.length);
            byte[] digest = ((MessageDigest) forwardDigest.clone()).digest();
            return Arrays.copyOfRange(digest, 0, 4);
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * calculate the backward digest
     * 
     * @param data
     * @return a four-byte array containing the digest
     */
    public byte[] calcBackwardDigest(byte[] data) {
        try {
            backwardDigest.update(data, 0, data.length);
            byte[] digest = ((MessageDigest) backwardDigest.clone()).digest();
            return Arrays.copyOfRange(digest, 0, 4);
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * encrypt data with symmetric key
     * 
     * @param data
     *            is used for input and output.
     */
    public void encrypt(byte[] data) {
        // encrypt data
        byte[] encrypted = encryptStreamCipher.processStream(data);
        // copy to output
        System.arraycopy(encrypted, 0, data, 0, Math.min(data.length, encrypted.length));
    }

    /**
     * decrypt data with symmetric key
     * 
     * @param data
     *            is used for input and output.
     */
    public void decrypt(byte[] data) {
        // decrypt data
        byte[] decrypted = decryptStreamCipher.processStream(data);
        // copy to output
        System.arraycopy(decrypted, 0, data, 0, Math.min(data.length, decrypted.length));
    }

    /**
     * helper function to convert a bigInteger to a fixed-sized array for
     * TOR-Usage
     */
    static byte[] BigIntegerTo128Bytes(BigInteger a) {
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
