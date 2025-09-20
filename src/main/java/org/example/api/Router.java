package org.example.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

public class Router {
    private static final ObjectMapper M = new ObjectMapper();

    private static class Route {
        final String method;
        final String pattern;
        final List<String> parts;
        final BiConsumer<HttpRequest, HttpResponse> handler;
        Route(String method, String pattern, BiConsumer<HttpRequest,HttpResponse> handler) {
            this.method = method.toUpperCase(Locale.ROOT);
            this.pattern = pattern;
            this.parts = split(pattern);
            this.handler = handler;
        }
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("GET", path, h)); return this; }
    public Router post(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("POST", path, h)); return this; }
    public Router put(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("PUT", path, h)); return this; }
    public Router delete(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("DELETE", path, h)); return this; }

    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        URI uri = ex.getRequestURI();
        String path = uri.getPath();
        List<String> req = split(path);
        for (Route r : routes) {
            if (!r.method.equals(method)) continue;
            Map<String,String> params = new HashMap<>();
            if (match(r.parts, req, params)) {
                HttpRequest reqW = new HttpRequest(ex, params, parseQuery(uri.getQuery()));
                HttpResponse resW = new HttpResponse(ex);
                try {
                    r.handler.accept(reqW, resW);
                } catch (Exception e) {
                    sendError(ex, 500, e.getMessage());
                }
                return;
            }
        }
        sendError(ex, 404, "not found");
    }

    private static List<String> split(String p) {
        if (p == null || p.isEmpty()) return List.of("");
        String s = p.startsWith("/") ? p.substring(1) : p;
        if (s.isEmpty()) return List.of("");
        return Arrays.asList(s.split("/"));
    }

    private static boolean match(List<String> pattern, List<String> path, Map<String,String> out) {
        if (pattern.size() != path.size()) return false;
        for (int i=0;i<pattern.size();i++) {
            String pp = pattern.get(i);
            String pv = path.get(i);
            if (pp.startsWith("{") && pp.endsWith("}")) {
                String name = pp.substring(1, pp.length()-1);
                out.put(name, pv);
            } else if (!pp.equals(pv)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String,String> parseQuery(String query){
        Map<String,String> map = new HashMap<>();
        if(query==null||query.isEmpty()) return map;
        for(String part: query.split("&")){
            int i = part.indexOf('=');
            if(i>0) map.put(part.substring(0,i), part.substring(i+1));
            else map.put(part, "");
        }
        return map;
    }

    private static void sendError(HttpExchange exchange, int status, String msg) throws IOException {
        byte[] b = ("{\"error\":\"" + (msg==null?"":msg.replace('"','\'')) + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
    }

    public static class HttpRequest {
        private final HttpExchange ex;
        private final Map<String,String> pathParams;
        private final Map<String,String> query;
        public HttpRequest(HttpExchange ex, Map<String,String> pathParams, Map<String,String> query){ this.ex=ex; this.pathParams=pathParams; this.query=query; }
        public String method(){ return ex.getRequestMethod(); }
        public String path(){ return ex.getRequestURI().getPath(); }
        public Map<String,String> pathParams(){ return pathParams; }
        public String pathParam(String name){ return pathParams.get(name); }
        public Map<String,String> query(){ return query; }
        public String query(String k){ return query.get(k); }
        public String header(String name){ return ex.getRequestHeaders().getFirst(name); }
        public <T> T readJson(TypeReference<T> typ) {
            try (InputStream is = ex.getRequestBody()) {
                return M.readValue(is, typ);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    public static class HttpResponse {
        private final HttpExchange ex;
        public HttpResponse(HttpExchange ex){ this.ex = ex; }
        public void json(int status, Object obj) {
            try {
                byte[] b = M.writeValueAsBytes(obj);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(status, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        public void text(int status, String body, String contentType) {
            try {
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", contentType==null?"text/plain; charset=utf-8":contentType);
                ex.sendResponseHeaders(status, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }
}
