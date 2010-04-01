/**
 * OnionCoffee - Anonymous Communication through TOR Network
 * Copyright (C) 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
package net.sf.onioncoffee.common;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.LoggerFactory;

/**
 * implements AES in Countermode. This special mode turn the block cipher into a
 * stream cipher. we thus have to create a key stream and take care that no byte
 * of it gets lost.
 * 
 * BD: Ported to Java Crypto July 2009
 * 
 * @author Brad Davis
 * @author Lexi Pimenidis
 */

public class AESCounterMode {
    private final static String ALGORITHM = "AES";
    private final Cipher cipher;
    private final int blockSize;
    private final byte[] counterBuffer;
    private byte[] cipherStreamBuffer;
    private int cipherStreamIndex;

    /**
     * init the AES-Engine
     * 
     * @param encrypt
     *            is the key-stream created with encryption or decryption? In
     *            case of doubt: set to TRUE
     * @param key
     *            the symmetric key for the algorithm
     * @throws InvalidKeyException 
     * @throws NoSuchPaddingException 
     * @throws NoSuchAlgorithmException 
     */
    public AESCounterMode(boolean encrypt, Key key) {
        if (!encrypt) {
            LoggerFactory.getLogger(getClass()).warn("WARNING! neve use Counter-mode in TOR with 'decryption'");
        }
        try {
            // init cipher
            cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key);
            blockSize = cipher.getBlockSize();
            counterBuffer = new byte[blockSize];
            cipherStreamBuffer = new byte[blockSize];
            cipherStreamIndex = blockSize;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public AESCounterMode(boolean encrypt, byte [] key)  {
        this(encrypt, new SecretKeySpec(key, ALGORITHM));
    }

    /**
     * reads the next key of the key stream from the buffer. if the buffer is
     * not filled, generates the next few bytes in the buffer.
     * 
     * @return the next byte of the key stream
     */
    private byte nextStreamByte() {
        ++cipherStreamIndex;
        // are there still unused bytes in the buffer?
        if (cipherStreamIndex >= blockSize) {
            // fill stream-buffer
            cipherStreamBuffer = cipher.update(counterBuffer);
            cipherStreamIndex = 0;
            // increase counterBuffer
            int j = blockSize - 1;
            do {
                ++counterBuffer[j];
                --j;
            } while ((counterBuffer[j + 1] == 0) && (j >= 0));
        }
        return cipherStreamBuffer[cipherStreamIndex];
    }

    public void processStream(byte[] src, byte [] dst) {
        processStream(src, 0, dst, 0);
    }

    public void processStream(byte[] src, int srcOffset, byte [] dst) {
        processStream(src, srcOffset, dst, 0);
    }

    public void processStream(byte[] src, byte [] dst, int dstOffset) {
        processStream(src, 0, dst, dstOffset);
    }

    public void processStream(byte[] src, int srcOffset, byte [] dst, int dstOffset) {
        processStream(src, srcOffset, dst, dstOffset, Math.min(src.length - srcOffset, dst.length - dstOffset));
    }

    public void processStream(byte[] src, int srcOffset, byte [] dst, int dstOffset, int length) {
        for (int i = 0; i < length; ++i) {
            dst[i + dstOffset] = (byte) ((src[i + srcOffset] + 256) ^ (nextStreamByte() + 256));
        }
    }
    
    public byte[] processStream(byte[] src, int srcOffset) {
        byte[] out = new byte[src.length - srcOffset];
        processStream(src, srcOffset, out);
        return out;

    }
        /**
     * encrypts or decrypts an array of arbitrary length. since counterBuffer mode is
     * used as a stream cipher, the cipher is symmetric, i.e. encryption and
     * decryption is the same.
     * 
     * @param in
     *            input the plain text, or the cipher text
     * @return receive the result
     */
    public byte[] processStream(byte[] in) {
        return processStream(in, 0);
    }

}
