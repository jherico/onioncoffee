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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;

import com.chaosinmotion.asn1.BerInputStream;
import com.chaosinmotion.asn1.BerOutputStream;
import com.chaosinmotion.asn1.Tag;
import com.google.common.base.Charsets;

/**
 * this class contains utility functions concerning encryption
 * 
 * @author Brad Davis
 * @author Lexi Pimenidis
 * @author Andriy Panchenko
 * @author Michael Koellejan
 */
public class Encryption {
    public static final String HASH_ALGORITHM = "SHA-1";
    private static final String PK_ALGORITHM = "RSA";
    private static final String CIPHER_INSTANCE = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
    private static final int OAEP_INPUT_BLOCK_SIZE = 86;
    private static final int OAEP_OUTPUT_BLOCK_SIZE = 128;
    private static final int OAEP_BLOCK_SIZE_DIFFERENCE = OAEP_OUTPUT_BLOCK_SIZE - OAEP_INPUT_BLOCK_SIZE;

    /**
     * checks signature of PKCS1-padded SHA1 hash of the input
     * 
     * @param signature
     *            signature to check
     * @param signingKey
     *            public key from signing
     * @param input
     *            byte array, signature is made over
     * 
     * @return true, if the signature is correct
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws SignatureException
     * 
     */
    public static boolean verifySignature(byte[] signature, PublicKey signingKey, byte[] data) {
        try {
            return verifySignature(PK_ALGORITHM, HASH_ALGORITHM, signature, signingKey, data);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static byte[] getHash(byte[] input) {
        try {
            return getHash(HASH_ALGORITHM, input);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * sign some data using a private kjey and PKCS#1 v1.5 padding
     * 
     * @param data
     *            the data to be signed
     * @param signingKey
     *            the key to sign the data
     * @return a signature
     */
    public static byte[] signData(byte[] data, PrivateKey signingKey) {
        try {
            return signData(PK_ALGORITHM, HASH_ALGORITHM, data, signingKey);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }


    public static byte[] asymmetricEncrypt(byte[] data, byte[] aesKey, Key key) throws GeneralSecurityException {
        byte[] result = new byte[data.length + OAEP_BLOCK_SIZE_DIFFERENCE];
        // initialize OAEP
        Cipher c = Cipher.getInstance(CIPHER_INSTANCE);
        c.init(Cipher.ENCRYPT_MODE, key);
        // apply RSA+OAEP
        c.doFinal(data, 0, OAEP_INPUT_BLOCK_SIZE, result);
        // initialize AES
        AESCounterMode aes = new AESCounterMode(true, aesKey);
        // apply AES
        aes.processStream(data, OAEP_INPUT_BLOCK_SIZE, result, OAEP_OUTPUT_BLOCK_SIZE);
        return result;
    }

    /**
     * encrypt data with asymmetric key. create asymmetrically encrypted data:<br>
     * <ul>
     * <li>OAEP padding [42 bytes] (RSA-encrypted)
     * <li>Symmetric key [16 bytes]
     * <li>First part of data [70 bytes]
     * <li>Second part of data [x-70 bytes] (Symmetrically encrypted)
     * <ul>
     * encrypt and store in result
     * 
     * @param priv
     *            key to use for decryption
     * @param data
     *            to be decrypted, needs currently to be at least 70 bytes long
     * @return raw data
     * @throws GeneralSecurityException
     */
    public static byte[] asymmetricDecrypt(byte[] data, byte[] aesKey, Key key) throws GeneralSecurityException {
        if (data == null) {
            throw new NullPointerException("can't encrypt NULL data");
        }
        if (data.length < 70) {
            throw new RuntimeException("input array too short");
        }

        byte[] result = new byte[Math.max(data.length - OAEP_BLOCK_SIZE_DIFFERENCE, 128)];
        // init OAEP
        Cipher c = Cipher.getInstance(CIPHER_INSTANCE);
        c.init(Cipher.DECRYPT_MODE, key);
        c.doFinal(data, 0, OAEP_OUTPUT_BLOCK_SIZE, result);
        // init AES
        AESCounterMode aes = new AESCounterMode(true, aesKey);
        // apply AES
        aes.processStream(data, OAEP_OUTPUT_BLOCK_SIZE, result, OAEP_INPUT_BLOCK_SIZE);
        if (data.length - OAEP_BLOCK_SIZE_DIFFERENCE < 128) {
            byte[] oldresult = result;
            result = new byte[data.length - OAEP_BLOCK_SIZE_DIFFERENCE];
            System.arraycopy(oldresult, 0, result, 0, result.length);
        }
        return result;
    }

    public static RSAPublicKey extractRSAKey(String s)  {
        try {
            byte[] data = Encoding.extractBase64Data(s);
            BerInputStream bis = new BerInputStream(new ByteArrayInputStream(data));
            bis.readBerTag();
            bis.readBerLength();
            bis.readBerTag();
            BigInteger modulus = new BigInteger(bis.readOctetString(true));
            bis.readBerTag();
            BigInteger exponent = new BigInteger(bis.readOctetString(true));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * converts a JCERSAPublicKey into PKCS1-encoding
     * 
     * @param rsaPublicKey
     * @return PKCS1-encoded RSA PUBLIC KEY
     * @throws IOException 
     */
    public static byte[] getPKCS1EncodingFromRSAPublicKey(RSAPublicKey rsaPublicKey) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            BerOutputStream bis = new BerOutputStream(os);
            byte[] data;
            data = rsaPublicKey.getModulus().toByteArray();
            bis.writeBerTag(Tag.INTEGER);
            bis.writeOctetString(data, 0, data.length);
            data = rsaPublicKey.getPublicExponent().toByteArray();
            bis.writeBerTag(Tag.INTEGER);
            bis.writeOctetString(data, 0, data.length);
            os.close();
            data = os.toByteArray();
            os = new ByteArrayOutputStream();
            bis = new BerOutputStream(os);
            bis.writeBerTag(Tag.SEQUENCE | Tag.CONSTRUCTED);
            bis.writeOctetString(data, 0, data.length);
            os.close();
            return os.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * returns the SHA-1 hash of the input
     * 
     */
    public static byte[] getHash(String algorithm, byte[] input) throws GeneralSecurityException {
        MessageDigest sha = MessageDigest.getInstance(algorithm);
        sha.reset();
        sha.update(input, 0, input.length);
        return sha.digest();
    }

    public static byte[] getHash(String algorithm, String input) throws GeneralSecurityException {
        return getHash(algorithm, input, Charsets.UTF_8);
    }

    public static byte[] getHash(String algorithm, String input, Charset charset) throws GeneralSecurityException {
        return getHash(algorithm, input.getBytes(charset));
    }

    public static boolean verifySignature(String cipherName, String hashName, byte[] signature, PublicKey signingKey, byte[] data) throws GeneralSecurityException { 
        Cipher cipher = Cipher.getInstance(cipherName);
        cipher.init(Cipher.DECRYPT_MODE, signingKey);
        return Arrays.equals(cipher.doFinal(signature), getHash(hashName, data));
    }

    public static byte[] signData(String cipherName, String hashName, byte[] data, PrivateKey signingKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(cipherName);
        cipher.init(Cipher.ENCRYPT_MODE, signingKey);
        return cipher.doFinal(getHash(hashName, data));
    }

}
