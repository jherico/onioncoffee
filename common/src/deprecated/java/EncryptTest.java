package net.sf.onioncoffee.test;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.KeyGenerator;

import net.sf.onioncoffee.common.AESCounterMode;
import net.sf.onioncoffee.common.Encoding;
import net.sf.onioncoffee.common.Encryption;
import net.sf.onioncoffee.common.OldAESCounterMode;
import net.sf.onioncoffee.common.OldEncryption;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Before;
import org.junit.Test;
import org.saintandreas.util.CryptoUtil;
import org.saintandreas.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chaosinmotion.asn1.BerOutputStream;
import com.chaosinmotion.asn1.Tag;

public class EncryptTest {
    private static final byte[] test_array = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 8, 7, 6, 5, 4, 3, 2, 1 };
    private KeyGenerator keyGenerator;
    private Key key;
    private KeyPairGenerator keyPairGenerator;
    private KeyPair keypair;
    private RSAPrivateKey npriv;
    private RSAPublicKey npub;
    private byte[] data;

    private final static Logger LOG = LoggerFactory.getLogger(EncryptTest.class);

    final byte[] symmetric_key_for_create = new byte[16];
    {
        new Random().nextBytes(symmetric_key_for_create);
    }

    @Before
    public void setup() throws NoSuchAlgorithmException, NoSuchProviderException, IOException {
        Security.addProvider(new BouncyCastleProvider());
        keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        key = keyGenerator.generateKey();

        keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());
        keypair = keyPairGenerator.generateKeyPair();
        npub = (RSAPublicKey) keypair.getPublic();
        npriv = (RSAPrivateKey) keypair.getPrivate();

        String buffer = StringUtil.readFromResource("/example.txt");
        data = buffer.getBytes();
    }

    @Test
    public void testEncryption() throws GeneralSecurityException, InvalidCipherTextException {
        byte[] hash = OldEncryption.getHash(test_array);
        byte[] newHash = CryptoUtil.getHash(Encryption.HASH_ALGORITHM, test_array);
        assertTrue(Arrays.equals(hash, newHash));

        RSAKeyParameters params = new RSAKeyParameters(true, npriv.getModulus(), npriv.getPrivateExponent());
        byte[] signature = OldEncryption.signData(test_array, params);
        byte[] newSignature = Encryption.signData(test_array, npriv);
        assertTrue(Arrays.equals(signature, newSignature));

        RSAPublicKeyStructure signingKey = new RSAPublicKeyStructure(npub.getModulus(), npub.getPublicExponent());
        boolean verify = OldEncryption.verifySignature(signature, signingKey, test_array);
        boolean newVerify = Encryption.verifySignature(signature, npub, test_array);
        assertTrue(verify && newVerify);
    }

    @Test
    public void testSymmetricEncryption() throws GeneralSecurityException {
        OldAESCounterMode newAes = new OldAESCounterMode(true, key.getEncoded());
        byte[] foo = newAes.processStream(data);
        AESCounterMode aes = new AESCounterMode(true, key);
        byte[] bar = aes.processStream(data);
        assertTrue(Arrays.equals(foo, bar));
        aes = new AESCounterMode(true, key);
        byte[] decrypted = aes.processStream(bar);
        assertTrue(Arrays.equals(decrypted, data));

    }

    @Test
    public void testDocumentSigning() throws IOException {
        String serverDescriptor = StringUtil.readFromResource("/examples/server.txt").replaceAll("\r\n", "\n");
        String publicKeyString = StringUtil.parseStringByRE(serverDescriptor, "signing-key\\s*(-----BEGIN RSA PUBLIC KEY-----.*?-----END RSA PUBLIC KEY-----)", "");
        PublicKey publicKey = Encryption.extractRSAKey(publicKeyString);
        String body = StringUtil.parseStringByRE(serverDescriptor, "(router .*?)-----BEGIN SIGNATURE-----", "");
        String sigString = StringUtil.parseStringByRE(serverDescriptor, "router .*?-----BEGIN SIGNATURE-----(.*?)-----END SIGNATURE-----", "");
        assertTrue(Encryption.verifySignature(Base64.decode(sigString), publicKey, body.getBytes()));
    }

    @Test
    public void testAsymmetricEncryption() throws GeneralSecurityException, InvalidCipherTextException {
        byte[] plaintext = new byte[100];
        new Random().nextBytes(plaintext);
        byte[] ciphertext = Encryption.asymmetricEncrypt(plaintext, symmetric_key_for_create, npub);
        byte[] newplaintext = Encryption.asymmetricDecrypt(ciphertext, symmetric_key_for_create, npriv);
        LOG.trace("Input:  " + Encoding.toHexString(plaintext));
        LOG.trace("Result: " + Encoding.toHexString(newplaintext));
        assertTrue(Arrays.equals(newplaintext, plaintext));
    }

    @Test
    public void testKeyExtraction() throws IOException, GeneralSecurityException {
        String serverDescriptor = StringUtil.readFromResource("/examples/server.txt").replaceAll("\r\n", "\n");
        String publicKeyString = StringUtil.parseStringByRE(serverDescriptor, "signing-key\\s*(-----BEGIN RSA PUBLIC KEY-----.*?-----END RSA PUBLIC KEY-----)", "");
        RSAPublicKey oldPublicKey = OldEncryption.extractRSAKey(publicKeyString);
        RSAPublicKey newPublicKey = Encryption.extractRSAKey(publicKeyString);
        assertTrue(oldPublicKey.getModulus().equals(newPublicKey.getModulus()));
        assertTrue(oldPublicKey.getPublicExponent().equals(newPublicKey.getPublicExponent()));
    }

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

    @Test
    public void testKeyEncoding() throws IOException {
        byte[] newEncoded = Encryption.getPKCS1EncodingFromRSAPublicKey(npub);
        byte[] oldEncoded = OldEncryption.getPKCS1EncodingFromRSAPublicKey(npub);
        assertTrue(Arrays.equals(oldEncoded, newEncoded));
    }
}
