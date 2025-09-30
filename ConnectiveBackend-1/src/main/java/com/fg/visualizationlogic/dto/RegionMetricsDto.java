package com.fg.visualizationlogic.dto;

public class RegionMetricsDto {
    private Long idRegion;
    private String codigoIso;           // útil para emparejar con GeoJSON
    private String nameRegion;          // opcional, si quieres usar nombre directamente
    private long totalHogares;
    private Double avgHabilidadesDigitales;
    private Double pctBarreraCosto;
    private Double avgPersonasPorHogar;
    private String ingresoModal;
    private Double indiceDesarrollo;    // 0..1

    public RegionMetricsDto() {}

    // getters / setters
    public Long getIdRegion() { return idRegion; }
    public void setIdRegion(Long idRegion) { this.idRegion = idRegion; }
    public String getCodigoIso() { return codigoIso; }
    public void setCodigoIso(String codigoIso) { this.codigoIso = codigoIso; }
    public String getNameRegion() { return nameRegion; }
    public void setNameRegion(String nameRegion) { this.nameRegion = nameRegion; }
    public long getTotalHogares() { return totalHogares; }
    public void setTotalHogares(long totalHogares) { this.totalHogares = totalHogares; }
    public Double getAvgHabilidadesDigitales() { return avgHabilidadesDigitales; }
    public void setAvgHabilidadesDigitales(Double avgHabilidadesDigitales) { this.avgHabilidadesDigitales = avgHabilidadesDigitales; }
    public Double getPctBarreraCosto() { return pctBarreraCosto; }
    public void setPctBarreraCosto(Double pctBarreraCosto) { this.pctBarreraCosto = pctBarreraCosto; }
    public Double getAvgPersonasPorHogar() { return avgPersonasPorHogar; }
    public void setAvgPersonasPorHogar(Double avgPersonasPorHogar) { this.avgPersonasPorHogar = avgPersonasPorHogar; }
    public String getIngresoModal() { return ingresoModal; }
    public void setIngresoModal(String ingresoModal) { this.ingresoModal = ingresoModal; }
    public Double getIndiceDesarrollo() { return indiceDesarrollo; }
    public void setIndiceDesarrollo(Double indiceDesarrollo) { this.indiceDesarrollo = indiceDesarrollo; }
}
