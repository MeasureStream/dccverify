package it.polito.dccverify.services
 
import it.polito.dccverify.dto.*
import org.apache.xml.security.Init
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.utils.Constants
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
 
@Service
class XmlSignatureVerifier(private val trustStore: TrustStoreService) {
 
    private val log = LoggerFactory.getLogger(javaClass)
 
    companion object {
        private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
        init {
            Init.init()   // inizializza Apache Santuario
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

        private fun secureFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

        fun verify(file: File): VerificationResult {
        val doc: Document = try {
            file.inputStream().use { secureFactory().newDocumentBuilder().parse(it) }
        } catch (e: Exception) {
            log.warn("XML non leggibile: {}", e.message)
            return VerificationResult(Verdict.UNREADABLE, VerificationChecks(),
                message = "Il file non è un XML valido.")
        }
 
        // Santuario deve sapere quali attributi sono identificatori,
        // altrimenti non riesce a risolvere i riferimenti interni della firma.
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            (all.item(i) as Element).let { el ->
                if (el.hasAttribute("Id")) el.setIdAttribute("Id", true)
                if (el.hasAttribute("id")) el.setIdAttribute("id", true)
            }
        }
 
        val sigElement = doc
            .getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature")
            .item(0) as? Element
            ?: return VerificationResult(Verdict.NOT_SIGNED, VerificationChecks(),
                message = "Il documento non contiene alcuna firma digitale.")
 
        return try {
            val sig = XMLSignature(sigElement, "")
            val cert = sig.keyInfo?.x509Certificate
                ?: return VerificationResult(Verdict.UNREADABLE,
                    VerificationChecks(signaturePresent = true),
                    message = "La firma non contiene il certificato del firmatario.")
 
            // 1. Integrità: il documento è stato modificato dopo la firma?
            val integrity = sig.checkSignatureValue(cert)
 
            // 2. Data della firma: da XAdES se presente, altrimenti "adesso".
            val signedAt = extractSigningTime(doc) ?: Date()
 
            // 3. Il certificato era valido a quella data?
            val validAtSigning = runCatching { cert.checkValidity(signedAt) }.isSuccess
 
            // 4. Risale a una CA che riconosciamo?
            val trusted = trustStore.isTrusted(cert, signedAt)
 
            val checks = VerificationChecks(true, integrity, trusted, validAtSigning)
            VerificationResult(
                verdict = decide(checks),
                checks = checks,
                signature = details(sig, cert, signedAt),
            )
        } catch (e: Exception) {
            log.warn("Verifica XML fallita: {}", e.message)
            VerificationResult(Verdict.UNREADABLE,
                VerificationChecks(signaturePresent = true), message = e.message)
        }
    }

    /** Ordine deliberato: prima la manomissione, poi la scadenza, poi la fiducia. */
    private fun decide(c: VerificationChecks): Verdict = when {
        !c.signaturePresent          -> Verdict.NOT_SIGNED
        !c.signatureIntegrity        -> Verdict.TAMPERED
        !c.certificateValidAtSigning -> Verdict.CERTIFICATE_EXPIRED
        !c.trustChain                -> Verdict.UNTRUSTED_ISSUER
        else                         -> Verdict.AUTHENTIC
    }
 
    private fun extractSigningTime(doc: Document): Date? {
        val nodes = doc.getElementsByTagNameNS(XADES_NS, "SigningTime")
        if (nodes.length == 0) return null
        return runCatching {
            Date.from(Instant.parse(nodes.item(0).textContent.trim()))
        }.getOrNull()
    }
 
    private fun details(sig: XMLSignature, cert: X509Certificate, signedAt: Date) =
        SignatureDetails(
            algorithm = sig.signedInfo.signatureMethodURI,
            signer = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serialNumber = cert.serialNumber.toString(16).uppercase(),
            signedAt = signedAt.toInstant().toString(),
            publicKeyHash = hashOf(cert.publicKey),
            documentHash = if (sig.signedInfo.length > 0)
                Base64.getEncoder().encodeToString(sig.signedInfo.item(0).digestValue) else null,
        )
 
    private fun hashOf(key: PublicKey): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(key.encoded))
}
