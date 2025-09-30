package com.fg.visualizationlogic.controller;

import com.fg.visualizationlogic.dto.RegionDetailDto;
import com.fg.visualizationlogic.dto.RegionMetricsDto;
import com.fg.visualizationlogic.service.MapDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/archivos/{idArchivo}")
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);
    private final MapDataService mapDataService;

    public MapController(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    /**
     * Devuelve métricas por región (lista para que el frontend pinte el geojson)
     *
     * Query params opcionales:
     * - ingresoCategoria
     * - tipoUso
     * - calidadServicio
     * - edad (5-14, 15-24, 25-60, mayores_60)
     * - genero (Mujer, Hombre)
     * - minIndiceHabilidades (int)
     * - onlyBarreraCosto (true/false)
     */
    @GetMapping("/map")
    public ResponseEntity<List<RegionMetricsDto>> getMap(
            @PathVariable("idArchivo") Long idArchivo,
            @RequestParam Map<String, String> allRequestParams
    ) {
        log.info("GET /map idArchivo={} params={}", idArchivo, allRequestParams);
        List<RegionMetricsDto> metrics = mapDataService.getMetricsByRegion(idArchivo, allRequestParams);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Devuelve detalle y desgloses para una región (panel lateral).
     * Mantiene aplicados los mismos filtros globales (se pasan como query params).
     */
    @GetMapping("/regions/{idRegion}")
    public ResponseEntity<RegionDetailDto> getRegionDetail(
            @PathVariable("idArchivo") Long idArchivo,
            @PathVariable("idRegion") Long idRegion,
            @RequestParam Map<String, String> allRequestParams
    ) {
        log.info("GET /regions/{} idArchivo={} params={}", idRegion, idArchivo, allRequestParams);
        RegionDetailDto detail = mapDataService.getRegionDetail(idArchivo, idRegion, allRequestParams);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }

    /**
     * (Opcional) Fuerza el recálculo de agregados por región para un archivo (UPSERT en region_aggregates).
     */
    @PostMapping("/aggregates/compute")
    public ResponseEntity<String> recomputeAggregates(@PathVariable("idArchivo") Long idArchivo) {
        log.info("POST /aggregates/compute idArchivo={}", idArchivo);
        mapDataService.computeRegionAggregates(idArchivo);
        return ResponseEntity.ok("Aggregates recomputed for idArchivo=" + idArchivo);
    }
}
