package com.appbana.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

public class Router {
    private static final ObjectMapper M = new ObjectMapper().findAndRegisterModules();

    private static class Route {
        final String method;
        final List<String> parts;
        final BiConsumer<HttpRequest, HttpResponse> handler;
        Route(String method, String pattern, BiConsumer<HttpRequest,HttpResponse> handler) {
            this.method = method.toUpperCase(Locale.ROOT);
            this.parts = split(pattern);
            this.handler = handler;
        }
    }

    private final List<Route> routes = new ArrayList<>();
    private final List<BiConsumer<HttpRequest, HttpResponse>> middlewares = new ArrayList<>();

    /**
     * Add middleware that runs before all route handlers.
     * Middleware can short-circuit by calling response methods.
     */
    public Router use(BiConsumer<HttpRequest, HttpResponse> middleware) {
        middlewares.add(middleware);
        return this;
    }

    public Router get(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("GET", path, h)); return this; }
    public Router post(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("POST", path, h)); return this; }
    public Router put(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("PUT", path, h)); return this; }
    public Router delete(String path, BiConsumer<HttpRequest,HttpResponse> h) { routes.add(new Route("DELETE", path, h)); return this; }

    public void handle(HttpExchange ex) throws IOException {
        // Add CORS headers for all requests
        Headers headers = ex.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        // Handle preflight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        URI uri = ex.getRequestURI();
        String path = uri.getPath();
        List<String> req = split(path);
        
        // Run middlewares first
        HttpRequest reqW = new HttpRequest(ex, new HashMap<>(), parseQuery(uri.getQuery()));
        HttpResponse resW = new HttpResponse(ex);
        for (BiConsumer<HttpRequest, HttpResponse> middleware : middlewares) {
            try {
                middleware.accept(reqW, resW);
                // If response was already sent by middleware (e.g., 429 rate limit), stop here
                if (resW.isSent()) {
                    return;
                }
            } catch (Exception e) {
                sendError(ex, 500, e.getMessage());
                return;
            }
        }
        
        // Route to handler
        for (Route r : routes) {
            if (!r.method.equals(method)) continue;
            Map<String,String> params = new HashMap<>();
            if (match(r.parts, req, params)) {
                reqW = new HttpRequest(ex, params, parseQuery(uri.getQuery()));
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

    // New: servlet-based handle for use with embedded containers (Tomcat, Jetty, etc.)
    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod().toUpperCase(Locale.ROOT);
        String path = req.getRequestURI();
        List<String> reqParts = split(path);
        for (Route r : routes) {
            if (!r.method.equals(method)) continue;
            Map<String,String> params = new HashMap<>();
            if (match(r.parts, reqParts, params)) {
                HttpRequestServlet reqW = new HttpRequestServlet(req, params, parseQuery(req.getQueryString()));
                HttpResponseServlet resW = new HttpResponseServlet(resp);
                try {
                    r.handler.accept(reqW, resW);
                } catch (Exception e) {
                    sendError(resp, 500, e.getMessage());
                }
                return;
            }
        }
        sendError(resp, 404, "not found");
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

    private static void sendError(HttpServletResponse resp, int status, String msg) throws IOException {
        byte[] b = ("{\"error\":\"" + (msg==null?"":msg.replace('"','\'')) + "\"}").getBytes(StandardCharsets.UTF_8);
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentLength(b.length);
        try (OutputStream os = resp.getOutputStream()) { os.write(b); }
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

    // Servlet-backed request wrapper
    public static class HttpRequestServlet extends HttpRequest {
        private final HttpServletRequest req;
        private final Map<String,String> pathParams;
        private final Map<String,String> query;
        public HttpRequestServlet(HttpServletRequest req, Map<String,String> pathParams, Map<String,String> query) {
            super(null, pathParams, query);
            this.req = req; this.pathParams = pathParams; this.query = query;
        }
        @Override public String method(){ return req.getMethod(); }
        @Override public String path(){ return req.getRequestURI(); }
        @Override public Map<String,String> pathParams(){ return pathParams; }
        @Override public String pathParam(String name){ return pathParams.get(name); }
        @Override public Map<String,String> query(){ return query; }
        @Override public String query(String k){ return query.get(k); }
        @Override public String header(String name){ return req.getHeader(name); }
        @Override public <T> T readJson(TypeReference<T> typ) {
            try (InputStream is = req.getInputStream()) {
                return M.readValue(is, typ);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    public static class HttpResponse {
        private final HttpExchange ex;
        private boolean sent = false;
        public HttpResponse(HttpExchange ex){ this.ex = ex; }
        public boolean isSent() { return sent; }
        public void json(int status, Object obj) {
            try {
                byte[] b = M.writeValueAsBytes(obj);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(status, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                sent = true;
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
                sent = true;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        public void bytes(int status, byte[] body, String contentType) {
            try {
                ex.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
                ex.sendResponseHeaders(status, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
                sent = true;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    // Servlet-backed response wrapper
    public static class HttpResponseServlet extends HttpResponse {
        private final HttpServletResponse resp;
        public HttpResponseServlet(HttpServletResponse resp) { super(null); this.resp = resp; }
        @Override public void json(int status, Object obj) {
            try {
                byte[] b = M.writeValueAsBytes(obj);
                resp.setStatus(status);
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                resp.setContentLength(b.length);
                try (OutputStream os = resp.getOutputStream()) { os.write(b); }
            } catch (IOException ioe) { throw new RuntimeException(ioe); }
        }
        @Override public void text(int status, String body, String contentType) {
            try {
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                resp.setStatus(status);
                resp.setContentType(contentType==null?"text/plain; charset=utf-8":contentType);
                resp.setContentLength(b.length);
                try (OutputStream os = resp.getOutputStream()) { os.write(b); }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        public void bytes(int status, byte[] body, String contentType) {
            try {
                resp.setStatus(status);
                resp.setContentType(contentType == null ? "application/octet-stream" : contentType);
                resp.setContentLength(body.length);
                try (OutputStream os = resp.getOutputStream()) {
                    os.write(body);
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }
}
