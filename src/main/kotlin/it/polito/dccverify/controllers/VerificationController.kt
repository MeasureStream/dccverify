package it.polito.dccverify.controllers
 
import it.polito.dccverify.dto.VerificationResult
import it.polito.dccverify.services.PdfSignatureVerifier
import it.polito.dccverify.services.TrustStoreService
import it.polito.dccverify.services.XmlSignatureVerifier
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
 
@RestController
@RequestMapping("/api/verify")
class VerificationController(
    private val xmlVerifier: XmlSignatureVerifier,
    private val pdfVerifier: PdfSignatureVerifier,
    private val trustStore: TrustStoreService,
) {
 
    @PostMapping("/xml", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun verifyXml(@RequestParam("file") file: MultipartFile): ResponseEntity<VerificationResult> =
        onTempFile(file, ".xml") { ResponseEntity.ok(xmlVerifier.verify(it)) }
 
    @PostMapping("/pdf", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun verifyPdf(@RequestParam("file") file: MultipartFile): ResponseEntity<VerificationResult> =
        onTempFile(file, ".pdf") { ResponseEntity.ok(pdfVerifier.verify(it)) }
 
    /** Trasparenza: dichiara pubblicamente contro quali CA verifichiamo. */
    @GetMapping("/trust-anchors")
    fun trustAnchors() = mapOf(
        "count" to trustStore.describe().size,
        "anchors" to trustStore.describe(),
    )
 
    /** Salva l’upload su file temporaneo, esegue il blocco, cancella sempre. */
    private fun <T> onTempFile(upload: MultipartFile, suffix: String, block: (File) -> T): T {
        require(!upload.isEmpty) { "Il file caricato è vuoto." }
        val temp = Files.createTempFile("verify-", suffix).toFile()
        return try {
            upload.transferTo(temp)
            block(temp)
        } finally {
            temp.delete()
        }
    }
}
