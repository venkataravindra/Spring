package com.dbs.edoc.notification.services.notification.ews;

import com.dbs.edoc.crypto.Crypter;
import com.dbs.edoc.crypto.CryptoUtilException;
import com.dbs.edoc.crypto.impl.DefaultCrypter;
import com.dbs.edoc.notification.error.ExchangeMessageException;
import com.dbs.edoc.notification.services.notification.Mail;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.property.MapiPropertyType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.property.complex.EmailAddress;
import microsoft.exchange.webservices.data.property.complex.MessageBody;
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class MailExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailExchangeService.class);
    private final Crypter crypter = DefaultCrypter.getAesInstance();

    abstract void initializeExchangeService(String username, String password, String domain) throws ExchangeMessageException;

    abstract ExchangeService getExchangeService();

    public void saveMailToOutbox(String senderEmail, List<String> emailPwdPair, Mail mail, byte[] attachment, String fileName) throws ExchangeMessageException, CryptoUtilException {
        if (emailPwdPair != null) {
            LOGGER.info("Domain and Password Pair found for [{}]", senderEmail);
            String safePwdKey = getPwdForEmail(emailPwdPair.get(0).trim());

            try {
                initializeExchangeService(senderEmail.trim(), safePwdKey, emailPwdPair.get(1).trim());
            } catch (Exception em) {
                LOGGER.error("Exception while initializing exchange service for [{}]", senderEmail);
                throw new ExchangeMessageException("Exception while initializing exchange service", em);
            }

            try {
                EmailMessage exchangeMessage = new EmailMessage(getExchangeService());
                for (String toAddress : mail.getTo()) {
                    exchangeMessage.getToRecipients().add(toAddress);
                }
                for (String ccAddress : mail.getCc()) {
                    exchangeMessage.getCcRecipients().add(ccAddress);
                }
                for (String bccAddress : mail.getBcc()) {
                    exchangeMessage.getBccRecipients().add(bccAddress);
                }
                if (attachment != null && !StringUtils.isBlank(fileName)) {
                    exchangeMessage.getAttachments().addFileAttachment(fileName, attachment);
                }
                ExtendedPropertyDefinition messageReadFlag = new ExtendedPropertyDefinition(3591, MapiPropertyType.Integer);
                exchangeMessage.setExtendedProperty(messageReadFlag, 1);
                exchangeMessage.setSubject(mail.getSubject());
                exchangeMessage.setBody(MessageBody.getMessageBodyFromText(mail.getBody()));
                exchangeMessage.setFrom(new EmailAddress(senderEmail));
                LOGGER.info("Attempting to save Email [{}] to the email box of [{}]", mail.getSubject(), senderEmail);
                exchangeMessage.save(WellKnownFolderName.SentItems);
                LOGGER.info("Mail [{}] Saved in the Sent items Folder of [{}]",mail.getSubject(), senderEmail);
            } catch (Exception e) {
                LOGGER.info("Error while saving to Sent items for [{}]",mail.getSubject());
                LOGGER.error(" Details of failure : ", e);
                throw new ExchangeMessageException("Error occurred while saving the message to Sent Items of [" + mail.getSubject() + "] ", e);
            }
        } else {
            LOGGER.warn("No Credentials information found for [{}]. Can not save the email in to Sent Items", senderEmail);
        }
    }

    private String getPwdForEmail(String password) throws CryptoUtilException {
        return crypter.decrypt(password);
    }


}
