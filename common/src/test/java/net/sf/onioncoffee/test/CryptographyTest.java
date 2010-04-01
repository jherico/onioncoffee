package net.sf.onioncoffee.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.KeyGenerator;

import net.sf.onioncoffee.common.AESCounterMode;
import net.sf.onioncoffee.common.Encryption;

import org.apache.commons.codec.binary.Base64;
import org.junit.Test;
import org.saintandreas.util.StringUtil;

public class CryptographyTest {
    private static final byte[] data; 
    static {
        try {
            data = StringUtil.readFromResource("/example.txt").getBytes();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    @Test
    public void testSignatures() throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());
        KeyPair keypair = keyPairGenerator.generateKeyPair(); 
        assertTrue(Encryption.verifySignature(Encryption.signData(data, keypair.getPrivate()), keypair.getPublic(), data));
    }

    @Test
    public void testSymmetricEncryption() throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128, new SecureRandom());
        Key key = keyGenerator.generateKey();
        byte[] decrypted = new AESCounterMode(true, key).processStream(new AESCounterMode(true, key).processStream(data));
        assertTrue(Arrays.equals(decrypted, data));
    }

    @Test
    public void testRSAKeyExtractionAndSignatureVerification() throws IOException {
        String serverDescriptor = StringUtil.readFromResource("/examples/server.txt").replaceAll("\r\n", "\n");
        String publicKeyString = StringUtil.parseStringByRE(serverDescriptor, "signing-key\\s*(-----BEGIN RSA PUBLIC KEY-----.*?-----END RSA PUBLIC KEY-----)", "");
        PublicKey publicKey = Encryption.extractRSAKey(publicKeyString);
        String body = StringUtil.parseStringByRE(serverDescriptor, "(router .*?)-----BEGIN SIGNATURE-----", "");
        String sigString = StringUtil.parseStringByRE(serverDescriptor, "router .*?-----BEGIN SIGNATURE-----(.*?)-----END SIGNATURE-----", "");
        assertTrue(Encryption.verifySignature(Base64.decodeBase64(sigString.getBytes()), publicKey, body.getBytes()));
    }

    @Test
    public void testAsymmetricEncryption() throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());
        KeyPair keypair = keyPairGenerator.generateKeyPair(); 
        final byte[] symmetric_key_for_create = new byte[16];
        new Random().nextBytes(symmetric_key_for_create);
        byte[] plaintext = new byte[100];
        new Random().nextBytes(plaintext);
        byte[] ciphertext = Encryption.asymmetricEncrypt(plaintext, symmetric_key_for_create, keypair.getPublic());
        byte[] newplaintext = Encryption.asymmetricDecrypt(ciphertext, symmetric_key_for_create, keypair.getPrivate());
        assertTrue(Arrays.equals(newplaintext, plaintext));
    }
    
    @Test
    public void testSha1Cloning() throws GeneralSecurityException, CloneNotSupportedException {
        MessageDigest digest = MessageDigest.getInstance(Encryption.HASH_ALGORITHM);
        digest.update(data, 0, data.length);
        
        byte[] digest1 = ((MessageDigest) digest.clone()).digest();
        byte[] digest2 = ((MessageDigest) digest.clone()).digest();
        assertTrue(Arrays.equals(digest1, digest2));
    }
}
