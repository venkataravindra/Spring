package com.dbs.edoc.notification.services.notification.graph;

import com.dbs.edoc.crypto.Crypter;
import com.dbs.edoc.crypto.CryptoUtilException;
import com.dbs.edoc.crypto.impl.DefaultCrypter;
import com.dbs.edoc.notification.error.ExchangeMessageException;
import com.dbs.edoc.notification.services.notification.Mail;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import okhttp3.Request;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public abstract class MailGraphService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailGraphService.class);
    private final Crypter crypter = DefaultCrypter.getAesInstance();

    // Abstract methods for Graph implementation
    abstract void initializeGraphService(String username, String password, String domain) throws ExchangeMessageException;
    abstract GraphServiceClient<Request> getGraphClient();

    /**
     * Save mail to Sent Items folder using Microsoft Graph API
     * This replaces the old saveMailToOutbox method that used EWS
     */
    public void saveMailToOutbox(String senderEmail, List<String> emailPwdPair, Mail mail, byte[] attachment, String fileName) 
            throws ExchangeMessageException, CryptoUtilException {
        
        if (emailPwdPair != null) {
            LOGGER.info("Domain and Password Pair found for [{}]", senderEmail);
            String safePwdKey = getPwdForEmail(emailPwdPair.get(0).trim());

            try {
                initializeGraphService(senderEmail.trim(), safePwdKey, emailPwdPair.get(1).trim());
            } catch (Exception em) {
                LOGGER.error("Exception while initializing graph service for [{}]", senderEmail);
                throw new ExchangeMessageException("Exception while initializing graph service", em);
            }

            try {
                // Create the email message using Graph API
                Message message = createGraphMessage(mail, senderEmail, attachment, fileName);
                
                LOGGER.info("Attempting to save Email [{}] to the email box of [{}]", mail.getSubject(), senderEmail);
                
                // Send the email and save to Sent Items
                sendAndSaveMessage(message, senderEmail);
                
                LOGGER.info("Mail [{}] Saved in the Sent items Folder of [{}]", mail.getSubject(), senderEmail);
                
            } catch (Exception e) {
                LOGGER.info("Error while saving to Sent items for [{}]", mail.getSubject());
                LOGGER.error("Details of failure: ", e);
                throw new ExchangeMessageException("Error occurred while saving the message to Sent Items of [" + mail.getSubject() + "] ", e);
            }
        } else {
            LOGGER.warn("No Credentials information found for [{}]. Can not save the email in to Sent Items", senderEmail);
        }
    }

    /**
     * Create a Graph Message object from Mail data
     */
    private Message createGraphMessage(Mail mail, String senderEmail, byte[] attachment, String fileName) 
            throws Exception {
        
        Message message = new Message();
        
        // Set subject
        message.subject = mail.getSubject();
        
        // Set body
        ItemBody body = new ItemBody();
        body.contentType = BodyType.TEXT;
        body.content = mail.getBody();
        message.body = body;
        
        // Set sender (from)
        Recipient fromRecipient = new Recipient();
        EmailAddress fromEmail = new EmailAddress();
        fromEmail.address = senderEmail;
        fromRecipient.emailAddress = fromEmail;
        message.from = fromRecipient;
        message.sender = fromRecipient;
        
        // Set To recipients
        List<Recipient> toRecipients = new ArrayList<>();
        for (String toAddress : mail.getTo()) {
            Recipient recipient = new Recipient();
            EmailAddress emailAddress = new EmailAddress();
            emailAddress.address = toAddress;
            recipient.emailAddress = emailAddress;
            toRecipients.add(recipient);
        }
        message.toRecipients = toRecipients;
        
        // Set CC recipients
        List<Recipient> ccRecipients = new ArrayList<>();
        for (String ccAddress : mail.getCc()) {
            if (StringUtils.isNotBlank(ccAddress)) {
                Recipient recipient = new Recipient();
                EmailAddress emailAddress = new EmailAddress();
                emailAddress.address = ccAddress;
                recipient.emailAddress = emailAddress;
                ccRecipients.add(recipient);
            }
        }
        message.ccRecipients = ccRecipients;
        
        // Set BCC recipients
        List<Recipient> bccRecipients = new ArrayList<>();
        for (String bccAddress : mail.getBcc()) {
            if (StringUtils.isNotBlank(bccAddress)) {
                Recipient recipient = new Recipient();
                EmailAddress emailAddress = new EmailAddress();
                emailAddress.address = bccAddress;
                recipient.emailAddress = emailAddress;
                bccRecipients.add(recipient);
            }
        }
        message.bccRecipients = bccRecipients;
        
        // Handle attachments
        if (attachment != null && !StringUtils.isBlank(fileName)) {
            List<Attachment> attachments = new ArrayList<>();
            
            // Create file attachment
            FileAttachment fileAttachment = new FileAttachment();
            fileAttachment.name = fileName;
            fileAttachment.contentBytes = Base64.getEncoder().encodeToString(attachment);
            fileAttachment.contentType = getContentType(fileName);
            
            attachments.add(fileAttachment);
            message.attachments = attachments;
        }
        
        // Note: ExtendedPropertyDefinition (3591, MapiPropertyType.Integer) for read flag
        // In Graph API, this is handled differently - we don't need to set it explicitly
        // as the message will be in Sent Items and will be marked as read
        
        return message;
    }

    /**
     * Send message and save to Sent Items
     */
    private void sendAndSaveMessage(Message message, String senderEmail) throws ClientException {
        GraphServiceClient<Request> graphClient = getGraphClient();
        
        // Build the request to send the message
        // This creates a draft in Sent Items and sends it
        graphClient.users(senderEmail)
                .sendMail(message, true)  // true = save to Sent Items
                .buildRequest()
                .post();
        
        LOGGER.info("Email sent and saved to Sent Items for [{}]", senderEmail);
    }

    /**
     * Get content type based on file extension
     */
    private String getContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".pdf")) return "application/pdf";
        if (lowerFileName.endsWith(".txt")) return "text/plain";
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) 
            return "application/msword";
        if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")) 
            return "application/vnd.ms-excel";
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) 
            return "image/jpeg";
        if (lowerFileName.endsWith(".png")) return "image/png";
        if (lowerFileName.endsWith(".xml")) return "application/xml";
        if (lowerFileName.endsWith(".csv")) return "text/csv";
        
        return "application/octet-stream";
    }

    /**
     * Decrypt password (same as before - no change needed)
     */
    private String getPwdForEmail(String password) throws CryptoUtilException {
        return crypter.decrypt(password);
    }

    /**
     * Optional: Additional helper methods for Graph API operations
     */
    
    /**
     * Get messages from a specific folder (replaces EWS FindItems)
     */
    public MessageCollectionPage getMessages(String userEmail, String folderName) throws ClientException {
        GraphServiceClient<Request> graphClient = getGraphClient();
        
        return graphClient.users(userEmail)
                .mailFolders(folderName)
                .messages()
                .buildRequest()
                .select("subject,from,receivedDateTime,body")
                .top(20)
                .get();
    }

    /**
     * Get message by ID (replaces EWS message retrieval)
     */
    public Message getMessageById(String userEmail, String messageId) throws ClientException {
        GraphServiceClient<Request> graphClient = getGraphClient();
        
        return graphClient.users(userEmail)
                .messages(messageId)
                .buildRequest()
                .select("subject,from,body,receivedDateTime")
                .get();
    }

    /**
     * Move message to different folder (replaces EWS Move)
     */
    public Message moveMessage(String userEmail, String messageId, String destinationFolderId) 
            throws ClientException {
        GraphServiceClient<Request> graphClient = getGraphClient();
        
        return graphClient.users(userEmail)
                .messages(messageId)
                .move(destinationFolderId)
                .buildRequest()
                .post();
    }
}