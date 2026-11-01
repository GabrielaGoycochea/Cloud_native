package cl.eventos.controller;

import cl.eventos.model.Compra;
import cl.eventos.repository.CompraRepository;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/resumenes")
public class ResumenController {

    private final CompraRepository compraRepository;
    private final S3Template s3Template;
    private final String bucket;

    public ResumenController(CompraRepository compraRepository,
                             S3Template s3Template,
                             @Value("${spring.cloud.aws.s3.bucket}") String bucket) {
        this.compraRepository = compraRepository;
        this.s3Template = s3Template;
        this.bucket = bucket;
    }

    // Generar el archivo físico (se descarga al computador)
    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> generar(@PathVariable Long id) {
        byte[] contenido = textoResumen(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resumen-" + id + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(contenido);
    }

    // Subir el resumen a S3, en la carpeta con el número del resumen
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
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) throws IOException {
        verificarExiste(id);
        S3Resource recurso = s3Template.download(bucket, clave(id));
        try (InputStream in = recurso.getInputStream()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resumen-" + id + ".txt")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(in.readAllBytes());
        }
    }

    // Borrar el archivo del resumen en S3
    @DeleteMapping("/{id}")
    public String borrar(@PathVariable Long id) {
        verificarExiste(id);
        s3Template.deleteObject(bucket, clave(id));
        return "Resumen " + id + " eliminado";
    }

    private String clave(Long id) {
        return id + "/resumen-" + id + ".txt";
    }

    private void guardar(Long id, String texto) {
        s3Template.upload(bucket, clave(id),
                new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private void verificarExiste(Long id) {
        if (!s3Template.objectExists(bucket, clave(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El resumen " + id + " no existe en S3");
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