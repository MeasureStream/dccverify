package it.polito.dccverify.dto
 
/** Esito complessivo della verifica. */
enum class Verdict {
    AUTHENTIC,            // firmato da noi, integro, certificato valido alla firma
    TAMPERED,             // la firma non corrisponde: il file è stato modificato
    UNTRUSTED_ISSUER,     // integro, ma firmato da una CA che non riconosciamo
    CERTIFICATE_EXPIRED,  // il certificato non era valido al momento della firma
    NOT_SIGNED,           // il file non contiene alcuna firma
    UNREADABLE,           // il file non è leggibile o non è del formato atteso
}
 
/** I tre controlli, ciascuno con il proprio esito. */
data class VerificationChecks(
    val signaturePresent: Boolean = false,
    val signatureIntegrity: Boolean = false,
    val trustChain: Boolean = false,
    val certificateValidAtSigning: Boolean = false,
)
 
/** Dati estratti dalla firma, da mostrare all’utente. */
data class SignatureDetails(
    val algorithm: String? = null,
    val signer: String? = null,        // a chi appartiene il certificato firmatario
    val issuer: String? = null,        // quale CA lo ha emesso
    val serialNumber: String? = null,
    val signedAt: String? = null,      // ISO-8601
    val publicKeyHash: String? = null,
    val documentHash: String? = null,
)
 

data class VerificationResult(
    val verdict: Verdict,
    val checks: VerificationChecks,
    val signature: SignatureDetails? = null,
    val message: String? = null,       // spiegazione leggibile, utile in caso di errore
)
