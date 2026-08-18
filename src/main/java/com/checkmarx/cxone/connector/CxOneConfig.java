package com.checkmarx.cxone.connector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Connection settings for {@link CxOneClient}, loaded from a Java
 * {@code .properties} file.
 *
 * <p>Expected keys (see {@code cxone.properties.sample} for a template):
 * <pre>
 *   cxone.iam.host   - IAM / Keycloak host, e.g. https://iam.checkmarx.net
 *   cxone.ast.host   - AST (CxOne) API host, e.g. https://ast.checkmarx.net
 *   cxone.tenant     - Tenant / realm name
 *   cxone.api.key    - CxOne API key, generated from the CxOne UI
 *   cxone.verify.ssl - true/false, defaults to true
 *   cxone.page.size  - default paging size, defaults to 100
 * </pre>
 */
public final class CxOneConfig {

    private final String iamHost;
    private final String astHost;
    private final String tenant;
    private final String apiKey;
    private final boolean verifySsl;
    private final int defaultPageSize;

    private CxOneConfig(String iamHost, String astHost, String tenant, String apiKey,
                         boolean verifySsl, int defaultPageSize) {
        this.iamHost = stripTrailingSlash(iamHost);
        this.astHost = stripTrailingSlash(astHost);
        this.tenant = tenant;
        this.apiKey = apiKey;
        this.verifySsl = verifySsl;
        this.defaultPageSize = defaultPageSize;
    }

    /** Load configuration from a properties file on the filesystem. */
    public static CxOneConfig fromFile(String propertiesFilePath) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(propertiesFilePath))) {
            props.load(in);
        }
        return fromProperties(props);
    }

    /** Load configuration from a properties file found on the classpath. */
    public static CxOneConfig fromClasspath(String resourceName) throws IOException {
        Properties props = new Properties();
        try (InputStream in = CxOneConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Properties resource not found on classpath: " + resourceName);
            }
            props.load(in);
        }
        return fromProperties(props);
    }

    private static CxOneConfig fromProperties(Properties props) {
        String iamHost = require(props, "cxone.iam.host");
        String astHost = require(props, "cxone.ast.host");
        String tenant = require(props, "cxone.tenant");
        String apiKey = require(props, "cxone.api.key");
        boolean verifySsl = Boolean.parseBoolean(props.getProperty("cxone.verify.ssl", "true"));
        int defaultPageSize = Integer.parseInt(props.getProperty("cxone.page.size", "100"));
        return new CxOneConfig(iamHost, astHost, tenant, apiKey, verifySsl, defaultPageSize);
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public String getIamHost() {
        return iamHost;
    }

    public String getAstHost() {
        return astHost;
    }

    public String getTenant() {
        return tenant;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }
}
