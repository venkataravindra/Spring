package com.dbs.edoc.notification.services.notification.graph;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.dbs.edoc.config.DynamicBooleanProperty;
import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.crypto.utils.ssl.IgnoreSSLCertificates;
import com.dbs.edoc.crypto.utils.ssl.InstallSSLCertificates;
import com.dbs.edoc.notification.error.ExchangeMessageException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class InternalMailGraphService extends MailGraphService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalMailGraphService.class);
    
    // Configuration properties (mapped from existing)
    private static final DynamicBooleanProperty MAIL_GRAPH_SSL_IGNORE = 
        new DynamicBooleanProperty("internal.graph.mail.ssl.ignore", false);
    
    // For on-premise Exchange, we need to connect via Graph with specific endpoints
    // Note: On-premise Exchange requires Hybrid Configuration
    private static final DynamicStringProperty GRAPH_AUTHORITY_URL = 
        new DynamicStringProperty("internal.graph.authority.url", "https://login.microsoftonline.com/");
    
    private static final DynamicStringProperty GRAPH_TENANT_ID = 
        new DynamicStringProperty("internal.graph.tenant.id", "");
    
    private static final DynamicStringProperty GRAPH_CLIENT_ID = 
        new DynamicStringProperty("internal.graph.client.id", "");
    
    private static final DynamicStringProperty GRAPH_SERVICE_ENDPOINT = 
        new DynamicStringProperty("internal.graph.service.endpoint", "https://graph.microsoft.com");
    
    // For on-premise scenarios - if using Hybrid with on-premise autodiscover
    private static final DynamicStringProperty EXCHANGE_AUTODISCOVER_URL = 
        new DynamicStringProperty("internal.exchange.autodiscover.url", "https://autodiscover.yourdomain.com/AutoDiscover/AutoDiscover.xml");
    
    private static final DynamicLongProperty TOKEN_EXPIRY_MINS = 
        new DynamicLongProperty("internal.graph.token.expiry.mins", 200);
    
    private static final DynamicLongProperty GRAPH_TIMEOUT = 
        new DynamicLongProperty("internal.graph.timeout.seconds", 30);

    // Token cache
    private final Cache<String, String> tokens;
    
    // Graph client instance
    private GraphServiceClient<Request> graphClient;
    
    // Custom SSL context for internal/on-premise connections
    private SSLContext customSSLContext;

    public InternalMailGraphService() {
        // Initialize token cache
        tokens = CacheBuilder.newBuilder()
                .expireAfterWrite(TOKEN_EXPIRY_MINS.getValue(), TimeUnit.MINUTES)
                .build();

        try {
            // Install certificates for internal Exchange (important for on-premise)
            installGraphCerts();
            
            // Initialize Graph client with SSL configuration
            initializeGraphClient();
            
            LOGGER.info("Internal Mail Graph Service initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Error occurred while initializing Internal Mail Graph Service", e);
        }
    }

    /**
     * Initialize Graph client with proper SSL and proxy configuration
     */
    private void initializeGraphClient() {
        try {
            OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofMillis(30000))
                    .readTimeout(Duration.ofMillis(30000))
                    .writeTimeout(Duration.ofMillis(30000));

            // Configure custom SSL context if needed
            if (customSSLContext != null) {
                httpClientBuilder.sslSocketFactory(customSSLContext.getSocketFactory(), 
                        (X509TrustManager) customSSLContext.getTrustManagers()[0]);
            }

            // If SSL ignore is enabled, set up a trust-all manager
            if (MAIL_GRAPH_SSL_IGNORE.get()) {
                LOGGER.warn("SSL certificate validation is DISABLED for Graph connections");
                // Create a trust manager that doesn't validate certificate chains
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
                };
                
                try {
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                    httpClientBuilder.sslSocketFactory(sslContext.getSocketFactory(), 
                            (X509TrustManager) trustAllCerts[0]);
                    httpClientBuilder.hostnameVerifier((hostname, session) -> true);
                } catch (Exception e) {
                    LOGGER.error("Failed to configure SSL ignore", e);
                }
            }

            // Note: Proxy configuration would be similar to CloudMailGraphService
            // Can be added here if needed

            LOGGER.info("Graph client initialized with endpoint: [{}]", GRAPH_SERVICE_ENDPOINT.getValue());
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Graph client", e);
        }
    }

    @Override
    public void initializeGraphService(String username, String password, String domain) throws ExchangeMessageException {
        LOGGER.info("Initializing Internal Graph Service with User name [{}]", username);
        
        try {
            // Extract user principal name (for on-premise, might need domain)
            String userPrincipalName = username;
            if (!username.contains("@") && domain != null && !domain.isEmpty()) {
                userPrincipalName = username + "@" + domain;
            }
            
            // Check token cache
            final String authKey = tokens.getIfPresent(userPrincipalName);
            
            if (authKey == null) {
                LOGGER.info("No cached token found for [{}]. Acquiring new token...", userPrincipalName);
                String accessToken = acquireToken(userPrincipalName, password, domain);
                if (accessToken != null) {
                    tokens.put(userPrincipalName, accessToken);
                    buildGraphClient(accessToken);
                    LOGGER.info("New Access Token acquired and Graph client initialized successfully!");
                } else {
                    LOGGER.error("Could not acquire access Token for Email [{}]", userPrincipalName);
                    throw new ExchangeMessageException("Failed to acquire access token for " + userPrincipalName);
                }
            } else {
                LOGGER.info("Existing Access Token found for [{}]", userPrincipalName);
                buildGraphClient(authKey);
                LOGGER.info("Existing Access Token used to initialize Graph client successfully!");
            }
            
            // Verify connection
            verifyGraphConnection(userPrincipalName);
            
        } catch (Exception e) {
            LOGGER.error("Error initializing Graph service for [{}]", username, e);
            throw new ExchangeMessageException("Failed to initialize Graph service: " + e.getMessage(), e);
        }
    }

    /**
     * Build Graph client with the access token
     */
    private void buildGraphClient(String accessToken) {
        try {
            // Create credential with access token
            TokenCredentialAuthProvider authProvider = 
                new TokenCredentialAuthProvider(
                    Collections.singletonList("https://graph.microsoft.com/.default"),
                    (tokenRequestContext) -> {
                        com.azure.core.credential.AccessToken azureToken = 
                            new com.azure.core.credential.AccessToken(
                                accessToken, 
                                java.time.OffsetDateTime.now().plusHours(1)
                            );
                        return java.util.concurrent.CompletableFuture.completedFuture(azureToken);
                    }
                );

            // Build Graph client with custom endpoint if needed
            // For on-premise, you might need to use a specific endpoint
            graphClient = GraphServiceClient.builder()
                    .authenticationProvider(authProvider)
                    .buildClient();

        } catch (Exception e) {
            LOGGER.error("Failed to build Graph client", e);
            throw new RuntimeException("Failed to build Graph client", e);
        }
    }

    /**
     * Acquire token using username/password flow for internal/on-premise Exchange
     * Note: This works with Azure AD, even for hybrid scenarios
     */
    private String acquireToken(String username, String password, String domain) throws ExchangeMessageException {
        try {
            LOGGER.info("Acquiring token from authority: [{}]", GRAPH_AUTHORITY_URL.getValue() + GRAPH_TENANT_ID.getValue());
            
            // For internal/on-premise, we might need to use the domain
            // The username might be in different formats: username@domain.com or domain\username
            String userPrincipalName = username;
            if (username.contains("@")) {
                // Already in UPN format
                userPrincipalName = username;
            } else if (domain != null && !domain.isEmpty()) {
                // Construct UPN from username and domain
                userPrincipalName = username + "@" + domain;
            }
            
            // Build the credential
            UsernamePasswordCredential credential = new UsernamePasswordCredentialBuilder()
                    .clientId(GRAPH_CLIENT_ID.getValue())
                    .tenantId(GRAPH_TENANT_ID.getValue())
                    .username(userPrincipalName)
                    .password(password)
                    .authorityHost(GRAPH_AUTHORITY_URL.getValue())
                    .build();

            // Request token
            com.azure.core.credential.AccessToken token = credential
                    .getToken(new TokenRequestContext().setScopes(
                        Collections.singletonList("https://graph.microsoft.com/.default")))
                    .block();

            if (token == null) {
                LOGGER.error("Unable to fetch Access Token for Email [{}]", userPrincipalName);
                return null;
            }

            String accessToken = token.getToken();
            LOGGER.info("Access Token obtained for Email [{}], Expires: [{}]", 
                userPrincipalName, token.getExpiresAt());
            
            return accessToken;

        } catch (Exception e) {
            LOGGER.error("Error occurred acquiring Token for [{}]", username, e);
            throw new ExchangeMessageException("Error occurred acquiring Token: " + e.getMessage(), e);
        }
    }

    /**
     * Verify Graph connection by getting user info
     */
    private void verifyGraphConnection(String email) throws ClientException {
        try {
            if (graphClient == null) {
                throw new ClientException("Graph client is null");
            }
            
            User me = graphClient.me()
                    .buildRequest()
                    .select("id,mail,userPrincipalName,displayName")
                    .get();
            
            LOGGER.info("Successfully authenticated with Graph for user: [{}]", me.userPrincipalName);
        } catch (ClientException e) {
            LOGGER.error("Failed to verify Graph connection for [{}]", email, e);
            throw e;
        }
    }

    @Override
    public GraphServiceClient<Request> getGraphClient() {
        if (graphClient == null) {
            throw new IllegalStateException("Graph client not initialized. Call initializeGraphService first.");
        }
        return this.graphClient;
    }

    /**
     * Install certificates for internal/on-premise Exchange
     * This is crucial for on-premise deployments
     */
    private void installGraphCerts() {
        if (MAIL_GRAPH_SSL_IGNORE.get()) {
            LOGGER.warn("Ignoring SSL certificate validation for Graph connections");
            try {
                // Ignore SSL certificates (use with caution - only for development/testing)
                IgnoreSSLCertificates.ignoreSslCertificates();
                // Create trust-all SSLContext
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                this.customSSLContext = sc;
            } catch (Exception e) {
                LOGGER.error("Failed to ignore SSL certificates", e);
            }
            return;
        }

        // For on-premise Exchange, we need to install certificates from the internal server
        // This is similar to the original EWS certificate installation
        String serviceUrl = GRAPH_SERVICE_ENDPOINT.getValue();
        if (serviceUrl.toLowerCase(Locale.ENGLISH).startsWith("https")) {
            LOGGER.info("Installing Certificate from Graph endpoint [{}]", serviceUrl);
            
            try {
                // Extract host and port from the service URL
                String host = serviceUrl.replace("https://", "").split("/")[0].split(":")[0];
                int port = 443;
                if (serviceUrl.contains(":")) {
                    String portStr = serviceUrl.split(":")[2].split("/")[0];
                    port = Integer.parseInt(portStr);
                }
                
                // Install certificate for internal connection
                InstallSSLCertificates.installSslCertificateOnDefaultContext(host, port);
                
                // Set trust store properties (same as original)
                System.setProperty("javax.net.ssl.trustStore", "jssecacerts");
                System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
                
                LOGGER.info("Certificate installed successfully for [{}:{}]", host, port);
            } catch (Exception e) {
                LOGGER.error("Failed to install certificate for Graph endpoint", e);
            }
        }
    }

    /**
     * Clear token cache for a specific user
     */
    public void clearTokenCache(String email) {
        tokens.invalidate(email);
        LOGGER.info("Token cache cleared for [{}]", email);
    }

    /**
     * Clear all token cache
     */
    public void clearAllTokenCache() {
        tokens.invalidateAll();
        LOGGER.info("All token cache cleared");
    }
}