package net.sf.onioncoffee.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.jce.provider.JCERSAPublicKey;
import org.bouncycastle.openssl.PEMReader;
import org.saintandreas.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OldEncryption {
    private static Logger LOG = LoggerFactory.getLogger(Encryption.class);
    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
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
    public static byte[] signData(byte[] data, CipherParameters params) {
        try {
            byte[] hash = CryptoUtil.getHash(Encryption.HASH_ALGORITHM, data);
            PKCS1Encoding pkcs1 = new PKCS1Encoding(new RSAEngine());
            pkcs1.init(true, params);
            return pkcs1.processBlock(hash, 0, hash.length);
        } catch (InvalidCipherTextException e) {
            LOG.error("", e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * returns the hash of the input
     * 
     * 
     */
    public static byte[] getHash(byte[] input) {

        SHA1Digest sha1 = new SHA1Digest();
        sha1.reset();
        sha1.update(input, 0, input.length);

        byte[] hash = new byte[sha1.getDigestSize()];
        sha1.doFinal(hash, 0);
        return hash;

    }

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
     * @throws InvalidCipherTextException
     * 
     */
    public static boolean verifySignature(byte[] signature, RSAPublicKeyStructure signingKey, byte[] input) throws InvalidCipherTextException {
        byte[] hash = getHash(input);
        RSAKeyParameters myRSAKeyParameters = new RSAKeyParameters(false, signingKey.getModulus(), signingKey.getPublicExponent());

        PKCS1Encoding pkcs_alg = new PKCS1Encoding(new RSAEngine());
        pkcs_alg.init(false, myRSAKeyParameters);

        byte[] decrypted_signature = pkcs_alg.processBlock(signature, 0, signature.length);

        return Arrays.equals(hash, decrypted_signature);
    }

    /**
     * makes RSA public key from string
     * 
     * @param s
     *            string that contais the key
     * @return
     * @see JCERSAPublicKey
     */
    public static RSAPublicKey extractRSAKey(String s) {
        PEMReader reader = new PEMReader(new StringReader(s));
        JCERSAPublicKey JCEKey = null;
        try {
            Object o = reader.readObject();
            if (!(o instanceof JCERSAPublicKey)) {
                throw new IOException("Common.extractRSAKey: no public key found for signing key in string '" + s + "'");
            }
            JCEKey = (JCERSAPublicKey) o;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return JCEKey;
    }

    /**
     * converts a JCERSAPublicKey into PKCS1-encoding
     * 
     * @param rsaPublicKey
     * @see JCERSAPublicKey
     * @return PKCS1-encoded RSA PUBLIC KEY
     */
    public static byte[] getPKCS1EncodingFromRSAPublicKey(PublicKey pubKey) {
        try {
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            ASN1OutputStream aOut = new ASN1OutputStream(bOut);
            aOut.writeObject(toASN1Object(pubKey));
            return bOut.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    protected static DERObject toASN1Object(PublicKey keyx) {
        RSAPublicKey key = (RSAPublicKey) keyx;
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new DERInteger(key.getModulus()));
        v.add(new DERInteger(key.getPublicExponent()));
        return new DERSequence(v);
    }
    
}
