package com.appbana;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

public class TomcatServer {
    private static final Logger LOG = LoggerFactory.getLogger(TomcatServer.class);

    public static void start(int port) throws Exception {
        AppConfig cfg = ConfigManager.getConfig();
        var router = ApiServer.buildRouter();

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(new File("./target/tomcat").getAbsolutePath());
        tomcat.setPort(port);
        // Trigger default connector creation
        tomcat.getConnector();

        Context ctx = tomcat.addContext("", new File(".").getAbsolutePath());

        // Single servlet to delegate all routes to our Router
        HttpServlet routerServlet = new HttpServlet() {
            @Override protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                // Optional HTTP->HTTPS redirect
                AppConfig cfgNow = ConfigManager.getConfig();
                boolean httpsEnabled = Boolean.TRUE.equals(cfgNow.getHttpsEnabled());
                boolean redirect = Boolean.TRUE.equals(cfgNow.getRedirectHttpToHttps());
                int httpsPort = cfgNow.getHttpsPort() != null ? cfgNow.getHttpsPort() : 8443;
                if (redirect && httpsEnabled && !req.isSecure()) {
                    String host = Optional.ofNullable(req.getHeader("Host")).orElse("localhost");
                    // strip port
                    String hostOnly = host;
                    int idx = host.indexOf(":");
                    if (idx >= 0) hostOnly = host.substring(0, idx);
                    String qs = req.getQueryString();
                    String loc = "https://" + hostOnly + ":" + httpsPort + req.getRequestURI() + (qs==null?"":"?"+qs);
                    resp.setStatus(308);
                    resp.setHeader("Location", loc);
                    return;
                }
                router.handle(req, resp);
            }
        };
        Tomcat.addServlet(ctx, "router", routerServlet);
        ctx.addServletMappingDecoded("/*", "router");

        // Optional HTTPS connector
        if (Boolean.TRUE.equals(cfg.getHttpsEnabled())) {
            int httpsPort = cfg.getHttpsPort() != null ? cfg.getHttpsPort() : 8443;
            String ksPath = cfg.getKeystorePath();
            String ksPass = cfg.getKeystorePassword();
            String keyPass = cfg.getKeyPassword() != null ? cfg.getKeyPassword() : ksPass;
            if (ksPath == null || ksPath.isBlank() || ksPass == null || ksPass.isBlank()) {
                LOG.error("HTTPS enabled but keystorePath/keystorePassword not provided; skipping HTTPS connector");
            } else {
                Connector https = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                https.setScheme("https");
                https.setSecure(true);
                https.setPort(httpsPort);
                Http11NioProtocol proto = (Http11NioProtocol) https.getProtocolHandler();
                proto.setSSLEnabled(true);

                SSLHostConfig sslHostConfig = new SSLHostConfig();
                // default host
                sslHostConfig.setHostName("_default_");
                SSLHostConfigCertificate cert = new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
                cert.setCertificateKeystoreFile(ksPath);
                cert.setCertificateKeystorePassword(ksPass);
                String lower = ksPath.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".p12") || lower.endsWith(".pkcs12")) {
                    cert.setCertificateKeystoreType("PKCS12");
                } else {
                    cert.setCertificateKeystoreType("JKS");
                }
                if (keyPass != null) {
                    cert.setCertificateKeyPassword(keyPass);
                }
                sslHostConfig.addCertificate(cert);
                proto.addSslHostConfig(sslHostConfig);

                tomcat.getService().addConnector(https);
                LOG.info("HTTPS connector enabled on port {}", httpsPort);
            }
        }

        tomcat.start();
        LOG.info("Tomcat server started on port {}{}", port, Boolean.TRUE.equals(cfg.getHttpsEnabled())?" (HTTPS also enabled)":"");
        // Do not block; Tomcat has non-daemon threads to keep JVM alive
    }
}
