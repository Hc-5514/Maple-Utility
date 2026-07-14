package com.maple.utility.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.maple.utility.config.NexonProperties;

@Service
public class ApiKeyCryptoService {

	private static final int AES_256_KEY_LENGTH = 32;
	private static final int GCM_IV_LENGTH = 12;
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final String AES_ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private final SecretKeySpec secretKey;
	private final SecureRandom secureRandom;

	@Autowired
	public ApiKeyCryptoService(NexonProperties properties) {
		this(properties, new SecureRandom());
	}

	ApiKeyCryptoService(NexonProperties properties, SecureRandom secureRandom) {
		byte[] key = Base64.getDecoder().decode(properties.apiKeySecret());
		if (key.length != AES_256_KEY_LENGTH) {
			throw new IllegalStateException("NEXON_API_KEY_SECRET must be a Base64 encoded 32-byte key");
		}
		this.secretKey = new SecretKeySpec(key, AES_ALGORITHM);
		this.secureRandom = secureRandom;
	}

	public String encrypt(String plainText) {
		try {
			byte[] iv = new byte[GCM_IV_LENGTH];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("API Key encryption failed", exception);
		}
	}

	public String decrypt(String encryptedText) {
		try {
			String[] parts = encryptedText.split(":", 2);
			if (parts.length != 2) {
				throw new IllegalArgumentException("Encrypted API Key format is invalid");
			}
			byte[] iv = Base64.getDecoder().decode(parts[0]);
			byte[] encrypted = Base64.getDecoder().decode(parts[1]);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new IllegalStateException("API Key decryption failed", exception);
		}
	}
}
