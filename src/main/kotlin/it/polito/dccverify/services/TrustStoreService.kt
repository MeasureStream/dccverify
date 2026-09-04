package it.polito.dccverify.services
 
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date
 
@Service
class TrustStoreService(
    @Value("\${dcc.trust-anchors-path}") private val trustAnchorsPath: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val anchors = mutableSetOf<TrustAnchor>()
    private val loaded = mutableListOf<X509Certificate>()
 
    /** @PostConstruct = eseguito una volta sola, all’avvio del servizio. */
    @PostConstruct
    fun load() {
        val dir = Path.of(trustAnchorsPath)
        if (!Files.isDirectory(dir)) {
            log.error("Cartella delle trust anchor non trovata: {}", dir)
            return
        }
        val factory = CertificateFactory.getInstance("X.509")
        Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().matches(Regex(".*\\.(crt|pem|cer)$")) }
                .forEach { file ->
                    runCatching {
                        Files.newInputStream(file).use { input ->
                            val cert = factory.generateCertificate(input) as X509Certificate
                            anchors.add(TrustAnchor(cert, null))
                            loaded.add(cert)
                            log.info("Trust anchor caricata: {} (valida fino al {})",
                                cert.subjectX500Principal.name, cert.notAfter)
                        }
                    }.onFailure { log.warn("File ignorato {}: {}", file.fileName, it.message) }
                }
        }
        if (anchors.isEmpty()) {
            log.error("NESSUNA trust anchor caricata: ogni verifica dara UNTRUSTED_ISSUER")
        }
    }
 
    /** Elenco leggibile, esposto dall’endpoint di trasparenza. */
    fun describe(): List<Map<String, String>> = loaded.map {
        mapOf(
            "subject" to it.subjectX500Principal.name,
            "issuer" to it.issuerX500Principal.name,
            "validUntil" to it.notAfter.toInstant().toString(),
        )
    }
 
    /**
     * Il certificato risale a una delle nostre CA, alla data indicata?
     * @param at data della FIRMA, non data odierna.
     */
    fun isTrusted(cert: X509Certificate, at: Date): Boolean {
        if (anchors.isEmpty()) return false
        return runCatching {
            val path = CertificateFactory.getInstance("X.509").generateCertPath(listOf(cert))
            val params = PKIXParameters(anchors).apply {
                isRevocationEnabled = false   // CRL/OCSP: fase 2
                date = at
            }
            CertPathValidator.getInstance("PKIX").validate(path, params)
            true
        }.onFailure { log.debug("Catena non valida: {}", it.message) }.isSuccess
    }
}