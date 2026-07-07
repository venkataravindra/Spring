package com.dbs.edoc.notification.services.notification.ews;

import com.dbs.edoc.config.DynamicIntProperty;
import com.dbs.edoc.config.DynamicLongProperty;
import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.notification.error.ExchangeMessageException;
import com.dbs.edoc.notification.util.ProxyRegistry;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.WebProxy;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.credential.WebProxyCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

@Service
public class CloudMailExchangeService extends MailExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudMailExchangeService.class);

    private ExchangeService exchangeService = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private static final DynamicStringProperty CLIENT_ID = new DynamicStringProperty("cloud.exchange.client.id", "");
    private static final DynamicStringProperty TENANT_ID = new DynamicStringProperty("cloud.exchange.tenant.id", "");
    private static final DynamicStringProperty AUTHORITY = new DynamicStringProperty("cloud.exchange.authority.url", "https://login.microsoftonline.com/");
    private static final DynamicStringProperty RESOURCE_URL = new DynamicStringProperty("cloud.exchange.resource.url", "https://outlook.office365.com");
    private static final DynamicStringProperty EXCHANGE_SERVICE_URL = new DynamicStringProperty("cloud.exchange.service.url", "https://outlook.office365.com/EWS/Exchange.asmx");
    private static final DynamicLongProperty TOKEN_EXPIRY_MINS = new DynamicLongProperty("cloud.exchange.token.expiry.mins", 200);
    private static final DynamicIntProperty EXCHANGE_SERVICE_TIMEOUT = new DynamicIntProperty("cloud.exchange.service.timeout", 30000);
    private static final DynamicLongProperty OUTLOOK_TIMEOUT = new DynamicLongProperty("cloud.exchange.outlook.timeout.seconds", 30);
    private final Cache<String, String> tokens;

    public CloudMailExchangeService() {

        tokens = CacheBuilder.newBuilder()
                .expireAfterWrite(TOKEN_EXPIRY_MINS.getValue(), TimeUnit.MINUTES)
                .build();

        try {
            ProxyRegistry.authenticateProxy();
            WebProxy webProxy = new WebProxy(ProxyRegistry.getHost(), Integer.parseInt(ProxyRegistry.getPort()), new WebProxyCredentials(ProxyRegistry.getUsername(), ProxyRegistry.getPassword(), null));
            exchangeService.setWebProxy(webProxy);
            exchangeService.setTimeout(EXCHANGE_SERVICE_TIMEOUT.getValue());
            exchangeService.setUrl(new URI(EXCHANGE_SERVICE_URL.getValue()));

            LOGGER.info("Initialized Cloud Mail Exchange Service");
        } catch (Exception ex) {
            LOGGER.error("Error occurred while initializing Cloud Mail Exchange Service", ex);
        }
    }

    @Override
    public void initializeExchangeService(String email, String password, String domain) throws ExchangeMessageException {
        ProxyRegistry.registerProxySettings();
        LOGGER.info("Initializing Exchange Web Service with User name [{}]", email);
        String firstName = email.split("@")[0].trim();
        LOGGER.info("First name extracted [{}] as Web credentials", firstName);
        exchangeService.setCredentials(new WebCredentials(firstName, password));

        final String authKey = tokens.getIfPresent(email);
        if (authKey == null) {
            String accessToken = acquireToken(email, password);
            if (accessToken != null) {
                tokens.put(email, accessToken);
                exchangeService.getHttpHeaders().put("Authorization", "Bearer " + accessToken);
                LOGGER.info("New Access Token updated in Headers successfully!");
            } else {
                LOGGER.error("Could not acquire access Token for Email [{}]", email);
            }
        } else {
            LOGGER.info("Existing Access Token found for [{}]", email);
            exchangeService.getHttpHeaders().put("Authorization", "Bearer " + authKey);
            LOGGER.info("Existing Access Token updated in Headers with successfully!");
        }
    }

    private String acquireToken(String email, String password) throws ExchangeMessageException {
        try {
            LOGGER.info("Authority URL [{}]", AUTHORITY.getValue() + TENANT_ID.getValue());
            AuthenticationContext context = new AuthenticationContext(AUTHORITY.getValue() + TENANT_ID.getValue(), false, executorService);
            context.setLogPii(true);
            Future<AuthenticationResult> authenticationResultFuture = context.acquireToken(RESOURCE_URL.getValue(), CLIENT_ID.getValue(), email, password, null);
            final AuthenticationResult authenticationResult = authenticationResultFuture.get(OUTLOOK_TIMEOUT.getValue(), TimeUnit.SECONDS);
            LOGGER.info("AuthenticationResult received : [{}]", authenticationResult);
            if (authenticationResult == null) {
                LOGGER.error(" Unable to fetch AuthenticationResult !! ");
            } else {
                final Date expiresOnDate = authenticationResult.getExpiresOnDate();
                String accessToken = authenticationResult.getAccessToken();
                LOGGER.info("Access Token found [{}] for Email [{}]", accessToken, email);
                LOGGER.info("Expiry Date {}", expiresOnDate);
                if (!accessToken.isEmpty()) {
                    return accessToken;
                } else {
                    LOGGER.error(" Unable to fetch accessToken! ");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error occurred acquiring Token", e);
            throw new ExchangeMessageException("Error occurred acquiring Token", e);
        }
        return null;
    }

    @Override
    public ExchangeService getExchangeService() {
        return this.exchangeService;
    }
}
