package it.polito.dccverify.services
 
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.signatures.SignatureUtil
import it.polito.dccverify.dtos.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.Date
 
@Service
class PdfSignatureVerifier(private val trustStore: TrustStoreService) {
 
    private val log = LoggerFactory.getLogger(javaClass)
 
    fun verify(file: File): VerificationResult = try {
        PdfReader(file).use { reader ->
            PdfDocument(reader).use { pdf ->
                val util = SignatureUtil(pdf)
                val names = util.signatureNames
                if (names.isEmpty()) {
                    VerificationResult(Verdict.NOT_SIGNED, VerificationChecks(),
                        message = "Il PDF non contiene firme digitali.")
                } else {
                    val pkcs7 = util.readSignatureData(names.first())
                    val integrity = pkcs7.verifySignatureIntegrityAndAuthenticity()
                    val cert = pkcs7.signingCertificate
                    val signedAt = pkcs7.signDate?.time ?: Date()
                    val validAtSigning = runCatching { cert.checkValidity(signedAt) }.isSuccess
                    val trusted = trustStore.isTrusted(cert, signedAt)
 
                    val checks = VerificationChecks(true, integrity, trusted, validAtSigning)
                    VerificationResult(
                        verdict = decide(checks),
                        checks = checks,
                        signature = SignatureDetails(
                            algorithm = runCatching { pkcs7.digestAlgorithmName }
                                .getOrDefault("sconosciuto"),
                            signer = cert.subjectX500Principal.name,
                            issuer = cert.issuerX500Principal.name,
                            serialNumber = cert.serialNumber.toString(16).uppercase(),
                            signedAt = signedAt.toInstant().toString(),
                            publicKeyHash = Base64.getEncoder().encodeToString(
                                MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)),
                            documentHash = null,
                        ),
                    )
                }
            }
        }
    } catch (e: Exception) {
        log.warn("Verifica PDF fallita: {}", e.message)
        VerificationResult(Verdict.UNREADABLE, VerificationChecks(), message = e.message)
    }
 
    private fun decide(c: VerificationChecks): Verdict = when {
        !c.signatureIntegrity        -> Verdict.TAMPERED
        !c.certificateValidAtSigning -> Verdict.CERTIFICATE_EXPIRED
        !c.trustChain                -> Verdict.UNTRUSTED_ISSUER
        else                         -> Verdict.AUTHENTIC
    }
}
