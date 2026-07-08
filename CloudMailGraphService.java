package com.dbs.edoc.notification.services.notification.ews;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.dbs.edoc.config.DynamicIntProperty;
import com.dbs.edoc.config.DynamicLongProperty;
import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.notification.error.ExchangeMessageException;
import com.dbs.edoc.notification.util.ProxyRegistry;
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

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CloudMailGraphService extends MailExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudMailGraphService.class);

    // Graph API configuration
    private static final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0";
    private static final List<String> SCOPES = Collections.singletonList("https://graph.microsoft.com/.default");

    // Dynamic configuration properties
    private static final DynamicStringProperty CLIENT_ID = new DynamicStringProperty("cloud.graph.client.id", "");
    private static final DynamicStringProperty TENANT_ID = new DynamicStringProperty("cloud.graph.tenant.id", "");
    private static final DynamicStringProperty AUTHORITY = new DynamicStringProperty("cloud.graph.authority.url", "https://login.microsoftonline.com/");
    private static final DynamicLongProperty TOKEN_EXPIRY_MINS = new DynamicLongProperty("cloud.graph.token.expiry.mins", 200);
    private static final DynamicIntProperty GRAPH_CLIENT_TIMEOUT = new DynamicIntProperty("cloud.graph.client.timeout", 30000);
    private static final DynamicLongProperty GRAPH_TIMEOUT = new DynamicLongProperty("cloud.graph.timeout.seconds", 30);

    // Cache for tokens (same as before)
    private final Cache<String, String> tokens;
    
    // Graph client instance
    private GraphServiceClient<Request> graphClient;

    public CloudMailGraphService() {
        // Initialize token cache (same as before)
        tokens = CacheBuilder.newBuilder()
                .expireAfterWrite(TOKEN_EXPIRY_MINS.getValue(), TimeUnit.MINUTES)
                .build();

        try {
            // Configure proxy if needed
            ProxyRegistry.authenticateProxy();
            
            // Initialize Graph client with proxy settings
            OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofMillis(GRAPH_CLIENT_TIMEOUT.getValue()))
                    .readTimeout(Duration.ofMillis(GRAPH_CLIENT_TIMEOUT.getValue()))
                    .writeTimeout(Duration.ofMillis(GRAPH_CLIENT_TIMEOUT.getValue()));

            // Set proxy if configured
            if (ProxyRegistry.getHost() != null && !ProxyRegistry.getHost().isEmpty()) {
                Proxy proxy = new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(ProxyRegistry.getHost(), Integer.parseInt(ProxyRegistry.getPort()))
                );
                httpClientBuilder.proxy(proxy);
                
                // Note: Proxy authentication is handled differently in Graph SDK
                // You may need to implement custom authentication interceptor
                LOGGER.info("Proxy configured for Graph client: {}:{}", 
                    ProxyRegistry.getHost(), ProxyRegistry.getPort());
            }

            LOGGER.info("Initialized Cloud Mail Graph Service");
        } catch (Exception ex) {
            LOGGER.error("Error occurred while initializing Cloud Mail Graph Service", ex);
        }
    }

    @Override
    public void initializeExchangeService(String email, String password, String domain) throws ExchangeMessageException {
        ProxyRegistry.registerProxySettings();
        LOGGER.info("Initializing Graph Service with User name [{}]", email);
        
        try {
            // Check if we have a valid token in cache
            final String authKey = tokens.getIfPresent(email);
            
            if (authKey == null) {
                LOGGER.info("No cached token found for [{}]. Acquiring new token...", email);
                String accessToken = acquireToken(email, password);
                if (accessToken != null) {
                    tokens.put(email, accessToken);
                    // Build Graph client with the new token
                    buildGraphClient(email, accessToken);
                    LOGGER.info("New Access Token acquired and Graph client initialized successfully!");
                } else {
                    LOGGER.error("Could not acquire access Token for Email [{}]", email);
                    throw new ExchangeMessageException("Failed to acquire access token for " + email);
                }
            } else {
                LOGGER.info("Existing Access Token found for [{}]", email);
                // Build Graph client with cached token
                buildGraphClient(email, authKey);
                LOGGER.info("Existing Access Token used to initialize Graph client successfully!");
            }
            
            // Verify the client works by getting user info
            verifyGraphConnection(email);
            
        } catch (Exception e) {
            LOGGER.error("Error initializing Graph service for [{}]", email, e);
            throw new ExchangeMessageException("Failed to initialize Graph service: " + e.getMessage(), e);
        }
    }

    private void buildGraphClient(String email, String accessToken) {
        try {
            // Create credential with access token
            // Note: We're using token directly since we already have it
            TokenCredentialAuthProvider authProvider = 
                new TokenCredentialAuthProvider(
                    SCOPES, 
                    (tokenRequestContext) -> {
                        // Return the cached token
                        com.azure.core.credential.AccessToken azureToken = 
                            new com.azure.core.credential.AccessToken(
                                accessToken, 
                                java.time.OffsetDateTime.now().plusHours(1)
                            );
                        return java.util.concurrent.CompletableFuture.completedFuture(azureToken);
                    }
                );

            // Build Graph client
            graphClient = GraphServiceClient.builder()
                    .authenticationProvider(authProvider)
                    .buildClient();

        } catch (Exception e) {
            LOGGER.error("Failed to build Graph client for [{}]", email, e);
            throw new RuntimeException("Failed to build Graph client", e);
        }
    }

    private void verifyGraphConnection(String email) throws ClientException {
        try {
            if (graphClient == null) {
                throw new ClientException("Graph client is null");
            }
            
            // Test connection by getting user info
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

    private String acquireToken(String email, String password) throws ExchangeMessageException {
        try {
            LOGGER.info("Acquiring token from authority: [{}]", AUTHORITY.getValue() + TENANT_ID.getValue());
            
            // Build the credential
            UsernamePasswordCredential credential = new UsernamePasswordCredentialBuilder()
                    .clientId(CLIENT_ID.getValue())
                    .tenantId(TENANT_ID.getValue())
                    .username(email)
                    .password(password)
                    .authorityHost(AUTHORITY.getValue())
                    .build();

            // Request token synchronously
            com.azure.core.credential.AccessToken token = credential
                    .getToken(new TokenRequestContext().setScopes(SCOPES))
                    .block();

            if (token == null) {
                LOGGER.error("Unable to fetch Access Token for Email [{}]", email);
                return null;
            }

            String accessToken = token.getToken();
            LOGGER.info("Access Token obtained for Email [{}], Expires: [{}]", 
                email, token.getExpiresAt());
            
            return accessToken;

        } catch (Exception e) {
            LOGGER.error("Error occurred acquiring Token for [{}]", email, e);
            throw new ExchangeMessageException("Error occurred acquiring Token: " + e.getMessage(), e);
        }
    }

    @Override
    public ExchangeService getExchangeService() {
        // This method is no longer needed - Graph doesn't use ExchangeService
        // Keep for backward compatibility or throw UnsupportedOperationException
        LOGGER.warn("getExchangeService() is deprecated. Use getGraphClient() instead.");
        throw new UnsupportedOperationException(
            "ExchangeService is no longer supported. Use Graph API via getGraphClient()"
        );
    }

    // New method to get Graph client - replaces getExchangeService()
    public GraphServiceClient<Request> getGraphClient() {
        if (graphClient == null) {
            throw new IllegalStateException("Graph client not initialized. Call initializeExchangeService first.");
        }
        return this.graphClient;
    }

    // Optional: Method to clear cache for a specific user
    public void clearTokenCache(String email) {
        tokens.invalidate(email);
        LOGGER.info("Token cache cleared for [{}]", email);
    }

    // Optional: Method to clear all cache
    public void clearAllTokenCache() {
        tokens.invalidateAll();
        LOGGER.info("All token cache cleared");
    }
}