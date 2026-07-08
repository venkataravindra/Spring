package com.dbs.edoc.notification.services.notification.ews;

import com.dbs.edoc.config.DynamicBooleanProperty;
import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.crypto.utils.ssl.IgnoreSSLCertificates;
import com.dbs.edoc.crypto.utils.ssl.InstallSSLCertificates;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.credential.ExchangeCredentials;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;

import static microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion.Exchange2010_SP2;

@Service
public class InternalMailExchangeService extends MailExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalMailExchangeService.class);
    private static final DynamicBooleanProperty MAIL_EXCHANGE_SSL_IGNORE = new DynamicBooleanProperty("internal.exchange.mail.ssl.ignore", false);
    private static final DynamicStringProperty EXCHANGE_SERVICE_URL = new DynamicStringProperty("internal.exchange.service.url", "https://webmail.uat1bank.dbs.com/EWS/Exchange.asmx");
    private static final DynamicStringProperty EXCHANGE_SERVICE_HOST_PORT = new DynamicStringProperty("internal.exchange.service.host.port", "webmail.uat1bank.dbs.com:443");
    private ExchangeService exchangeService;

    @Autowired
    public InternalMailExchangeService() {

        try {
            installMailExchangeCerts();
            this.exchangeService = new ExchangeService(Exchange2010_SP2);
            this.exchangeService.setUrl(new URI(EXCHANGE_SERVICE_URL.get()));
            LOGGER.info("Mail Exchange Service initialized to [{}]", EXCHANGE_SERVICE_URL.getValue());
        } catch (Exception e) {
            LOGGER.error("Exchange Server could not be initialized to [{}]. Will not be saving outgoing messages for on-premise email accounts", EXCHANGE_SERVICE_URL.getValue());
        }

    }

    public void initializeExchangeService(String username, String password, String domain) {
        LOGGER.info("Initializing Exchange Web Service with User name [{}]", username);
        String user = username.split("@")[0];
        LOGGER.info("First name extracted [{}] as Web credentials", user);
        ExchangeCredentials credentials = new WebCredentials(user, password, domain);
        exchangeService.setCredentials(credentials);
    }

    public ExchangeService getExchangeService() {
        return this.exchangeService;
    }

    private void installMailExchangeCerts() {
        if (MAIL_EXCHANGE_SSL_IGNORE.get()) {
            LOGGER.warn("Ignoring ssl certificate for: {}", EXCHANGE_SERVICE_URL.get());
            IgnoreSSLCertificates.ignoreSslCertificates();
            return;
        }

        if (EXCHANGE_SERVICE_URL.get().toLowerCase(Locale.ENGLISH).startsWith("https")) {
            LOGGER.info("Installing Certificate from [{}]", EXCHANGE_SERVICE_URL.get());
            final String[] hostPort = EXCHANGE_SERVICE_HOST_PORT.get().split(":");
            InstallSSLCertificates.installSslCertificateOnDefaultContext(hostPort[0], Integer.parseInt(hostPort[1]));

            System.setProperty("javax.net.ssl.trustStore", "jssecacerts");
            System.setProperty("javax.net.ssl.trustStorePassword","changeit");

        }
    }

}
