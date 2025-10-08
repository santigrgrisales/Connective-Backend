package com.fg.geminichatproxy.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO que recibe el frontend.
 */
public class ChatProxyRequest {
    public String archivoId;
    public List<Map<String,String>> history; // [{role:"user", content:"..."}, ...]
    public String userMessage;
    public Map<String,Object> context;
}
