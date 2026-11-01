package cl.eventos.dto;

import java.math.BigDecimal;

public record CompraResponse(Long numeroResumen, String evento, Integer cantidadEntradas,
                             BigDecimal precioUnitario, BigDecimal precioTotal) {
}
