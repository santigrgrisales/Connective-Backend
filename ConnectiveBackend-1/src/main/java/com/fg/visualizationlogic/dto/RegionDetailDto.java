package com.fg.visualizationlogic.dto;

import java.util.Map;

public class RegionDetailDto {
    private Long idRegion;
    private String nameRegion;
    private long totalHogares;
    private Double indiceDesarrollo;
    private Double avgHabilidadesDigitales;
    private Double pctBarreraCosto;
    private Map<String, Integer> tipoUsoCounts;        // e.g. {"Entretenimiento": 123, ...}
    private Map<String, Integer> razonNoInternetTop;   // top razones (la clave: razon, valor: count)
    private Map<String, Integer> ingresoDistribution;  // counts por ingreso_categoria
    private Map<String, Integer> edadGroups;           // counts por grupo de edad (5-14, 15-24,...)
    private Map<String, Integer> generoCounts;         // {"Mujer": x, "Hombre": y}

    public RegionDetailDto() {}

    // getters / setters
    public Long getIdRegion() { return idRegion; }
    public void setIdRegion(Long idRegion) { this.idRegion = idRegion; }
    public String getNameRegion() { return nameRegion; }
    public void setNameRegion(String nameRegion) { this.nameRegion = nameRegion; }
    public long getTotalHogares() { return totalHogares; }
    public void setTotalHogares(long totalHogares) { this.totalHogares = totalHogares; }
    public Double getIndiceDesarrollo() { return indiceDesarrollo; }
    public void setIndiceDesarrollo(Double indiceDesarrollo) { this.indiceDesarrollo = indiceDesarrollo; }
    public Double getAvgHabilidadesDigitales() { return avgHabilidadesDigitales; }
    public void setAvgHabilidadesDigitales(Double avgHabilidadesDigitales) { this.avgHabilidadesDigitales = avgHabilidadesDigitales; }
    public Double getPctBarreraCosto() { return pctBarreraCosto; }
    public void setPctBarreraCosto(Double pctBarreraCosto) { this.pctBarreraCosto = pctBarreraCosto; }
    public Map<String, Integer> getTipoUsoCounts() { return tipoUsoCounts; }
    public void setTipoUsoCounts(Map<String, Integer> tipoUsoCounts) { this.tipoUsoCounts = tipoUsoCounts; }
    public Map<String, Integer> getRazonNoInternetTop() { return razonNoInternetTop; }
    public void setRazonNoInternetTop(Map<String, Integer> razonNoInternetTop) { this.razonNoInternetTop = razonNoInternetTop; }
    public Map<String, Integer> getIngresoDistribution() { return ingresoDistribution; }
    public void setIngresoDistribution(Map<String, Integer> ingresoDistribution) { this.ingresoDistribution = ingresoDistribution; }
    public Map<String, Integer> getEdadGroups() { return edadGroups; }
    public void setEdadGroups(Map<String, Integer> edadGroups) { this.edadGroups = edadGroups; }
    public Map<String, Integer> getGeneroCounts() { return generoCounts; }
    public void setGeneroCounts(Map<String, Integer> generoCounts) { this.generoCounts = generoCounts; }
}
