package de.kempmobil.billing

import android.util.Base64
import timber.log.Timber
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

/**
 * Security-related billing methods. For a secure implementation, all of this code should be implemented
 * on a server that communicates with the application on the device. For the sake of simplicity and
 * clarity of this example, this code is included here and is executed on the device. If you must
 * verify the purchases on the phone, you should obfuscate this code to make it harder for an attacker
 * to replace the code with stubs that treat all purchases as verified.
 */
private const val KEY_FACTORY_ALGORITHM = "RSA"
private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

/**
 * Verifies that the data was signed with the given signature, and returns
 * the verified purchase. The data is in JSON format and signed
 * with a private key. The data also contains the [PurchaseState]
 * and product ID of the purchase.
 *
 * @param base64PublicKey the base64-encoded public key to use for verifying.
 * @param signedData the signed JSON string (signed, not encrypted)
 * @param signature the signature for the data, signed with the private key
 */
fun verifyPurchase(base64PublicKey: String?, signedData: String?, signature: String?): Boolean {
    if (signedData.isNullOrEmpty() || base64PublicKey.isNullOrEmpty() || signature.isNullOrEmpty()) {
        Timber.e("Purchase verification failed: missing data.")
        return false
    }

    val key = generatePublicKey(base64PublicKey)
    return verify(key, signedData, signature)
}

/**
 * Generates a PublicKey instance from a string containing the
 * Base64-encoded public key.
 *
 * @param encodedPublicKey Base64-encoded public key
 * @throws IllegalArgumentException if encodedPublicKey is invalid
 */
private fun generatePublicKey(encodedPublicKey: String): PublicKey {
    try {
        val decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException(e)
    } catch (e: InvalidKeySpecException) {
        Timber.e("Invalid key specification.")
        throw IllegalArgumentException(e)
    }

}

/**
 * Verifies that the signature from the server matches the computed
 * signature on the data.  Returns true if the data is correctly signed.
 *
 * @param publicKey public key associated with the developer account
 * @param signedData signed data from server
 * @param signature server signature
 * @return true if the data and signature match
 */
private fun verify(publicKey: PublicKey, signedData: String, signature: String): Boolean {
    val signatureBytes: ByteArray
    try {
        signatureBytes = Base64.decode(signature, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        Timber.e("Base64 decoding failed.")
        return false
    }

    try {
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initVerify(publicKey)
        sig.update(signedData.toByteArray())
        if (!sig.verify(signatureBytes)) {
            Timber.e("Signature verification failed.")
            return false
        }
        return true
    } catch (e: NoSuchAlgorithmException) {
        Timber.e("NoSuchAlgorithmException.")
    } catch (e: InvalidKeyException) {
        Timber.e("Invalid key specification.")
    } catch (e: SignatureException) {
        Timber.e("Signature exception.")
    }

    return false
}