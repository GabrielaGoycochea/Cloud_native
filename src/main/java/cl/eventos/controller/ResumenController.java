package cl.eventos.controller;

import cl.eventos.model.Compra;
import cl.eventos.repository.CompraRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
@RequestMapping("/api/resumenes")
public class ResumenController {

    private final CompraRepository compraRepository;
    private final S3Client s3Client;
    private final String bucket;

    public ResumenController(CompraRepository compraRepository,
                             S3Client s3Client,
                             @Value("${aws.s3.bucket}") String bucket) {
        this.compraRepository = compraRepository;
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    // Generar el archivo fisico del resumen (se descarga)
    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> generar(@PathVariable Long id) {
        byte[] contenido = textoResumen(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resumen-" + id + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(contenido);
    }

    // Subir el resumen a S3, en la carpeta con el numero del resumen
    @PostMapping("/{id}/s3")
    public String subir(@PathVariable Long id) {
        guardar(id, textoResumen(id));
        return "Resumen " + id + " subido a s3://" + bucket + "/" + clave(id);
    }

    // Modificar el archivo del resumen en S3
    @PutMapping("/{id}")
    public String modificar(@PathVariable Long id, @RequestBody String nuevoContenido) {
        verificarExiste(id);
        guardar(id, nuevoContenido);
        return "Resumen " + id + " modificado";
    }

    // Descargar el archivo del resumen desde S3
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        verificarExiste(id);
        byte[] contenido = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(clave(id)).build()).asByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resumen-" + id + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(contenido);
    }

    // Borrar el archivo del resumen en S3
    @DeleteMapping("/{id}")
    public String borrar(@PathVariable Long id) {
        verificarExiste(id);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave(id)).build());
        return "Resumen " + id + " eliminado";
    }

    private String clave(Long id) {
        return id + "/resumen-" + id + ".txt";
    }

    private void guardar(Long id, String texto) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(clave(id)).contentType("text/plain").build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private void verificarExiste(Long id) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(clave(id)).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El resumen " + id + " no existe en S3");
            }
            throw e;
        }
    }

    private String textoResumen(Long id) {
        Compra c = compraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
        return "RESUMEN DE COMPRA Nro. " + c.getId() + "\n"
                + "Evento: " + c.getEvento().getNombre() + "\n"
                + "Fecha del evento: " + c.getEvento().getFecha() + "\n"
                + "Entradas: " + c.getCantidad() + "\n"
                + "Precio unitario: " + c.getPrecioUnitario() + "\n"
                + "Precio total: " + c.getPrecioTotal() + "\n";
    }
}
