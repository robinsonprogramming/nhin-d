/* 
Copyright (c) 2010, NHIN Direct Project
All rights reserved.

Authors:
   Greg Meyer      gm2552@cerner.com
 
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
in the documentation and/or other materials provided with the distribution.  Neither the name of the The NHIN Direct Project (nhindirect.org). 
nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS 
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.nhindirect.gateway.smtp.james.mailet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.nhindirect.common.tx.TxUtil;
import org.nhindirect.common.tx.model.Tx;
import org.nhindirect.common.tx.model.TxMessageType;
import org.nhindirect.gateway.GatewayConfiguration;
import org.nhindirect.gateway.smtp.NotificationProducer;
import org.nhindirect.gateway.smtp.NotificationSettings;
import org.nhindirect.gateway.smtp.ReliableDispatchedNotificationProducer;
import org.nhindirect.gateway.smtp.dsn.DSNCreator;
import org.nhindirect.gateway.smtp.dsn.provider.FailedDeliveryDSNCreatorProvider;
import org.nhindirect.stagent.NHINDAddress;
import org.nhindirect.stagent.NHINDAddressCollection;
import org.nhindirect.stagent.mail.Message;
import org.nhindirect.stagent.mail.notifications.NotificationMessage;
import org.nhindirect.stagent.options.OptionsManager;


import com.google.inject.Provider;

/**
 * This mailet override the built in Apache James LocalDelivery mailet and sends an MDN dispatched message on successful delivery to a local mailbox
 * if the message request timely and reliable message delivery.
 * In addition, it also sends a DSN failure message if the message cannot be placed into the local mailbox.
 * @author Greg Meyer
 * @since 2.0
 */
public class TimelyAndReliableLocalDelivery extends AbstractNotificationAwareMailet
{
	private static final Log LOGGER = LogFactory.getFactory().getInstance(TimelyAndReliableLocalDelivery.class);	

	protected static final String DISPATCHED_MDN_DELAY = "DispatchedMDNDelay";
	
	/*
	 * Annotated resources are used for James 3 LocalDelivery mailet support
	 * This is completely experimental
	 */
	@Resource(name = "recipientrewritetable")
    private Object rrt;
	@Resource(name = "usersrepository")
	private Object usersRepository;
	@Resource(name = "mailboxmanager")
	private Object mailboxManager;
	@Resource(name = "domainlist")
	private Object domainList;
	@Resource(name = "filesystem")
	private Object fileSystem;
		
	protected Object localDeliveryMailet;
	
	protected Method serviceMethod;
	
	protected NotificationProducer notificationProducer;
	
	protected int dispatchedMDNDelay;
	
	static
	{		
		initJVMParams();
	}
	
	private synchronized static void initJVMParams()
	{
		/*
		 * Mailet configuration parameters
		 */
		final Map<String, String> JVM_PARAMS = new HashMap<String, String>();
		JVM_PARAMS.put(DISPATCHED_MDN_DELAY, "org.nhindirect.gateway.smtp.james.mailet.DispatchedMDNDelay");
		
		OptionsManager.addInitParameters(JVM_PARAMS);
	}	
	
	/**
	 * {@inheritDoc}
	 */
	public void init() throws MessagingException
	{
		super.init();
		

		
		try
		{
			final String sDispatchedDelay =  GatewayConfiguration.getConfigurationParam(DISPATCHED_MDN_DELAY,
					this, "0"); 
			
			try
			{
				dispatchedMDNDelay = Integer.valueOf(sDispatchedDelay).intValue();
			}
			catch (NumberFormatException e)
			{
				// in case of parsing exceptions
				dispatchedMDNDelay = 0;
			}
		
			// create an instance of the local delivery if we can
			localDeliveryMailet = createLocalDeliveryClass();
			
			final Method initMethod = Mailet.class.getDeclaredMethod("init", MailetConfig.class);
			
			serviceMethod = Mailet.class.getDeclaredMethod("service", Mail.class);
			
			// set private objects if they exist
			final Class<?> localDeliveryMailetClass = localDeliveryMailet.getClass();
			Field field = getDeclaredFieldQuietly(localDeliveryMailetClass, "rrt");
			if (field != null)
			{
				field.setAccessible(true);
				field.set(localDeliveryMailet, rrt);
			}
			
			field = getDeclaredFieldQuietly(localDeliveryMailetClass, "usersRepository");
			if (field != null)
			{
				field.setAccessible(true);
				field.set(localDeliveryMailet, usersRepository);
			}
			
			field = getDeclaredFieldQuietly(localDeliveryMailetClass, "mailboxManager");
			if (field != null)
			{
				field.setAccessible(true);
				field.set(localDeliveryMailet, mailboxManager);
			}
			
			
			field = getDeclaredFieldQuietly(localDeliveryMailetClass, "domainList");
			if (field != null)
			{
				field.setAccessible(true);
				field.set(localDeliveryMailet, domainList);
			}
			
			field = getDeclaredFieldQuietly(localDeliveryMailetClass, "fileSystem");
			if (field != null)
			{
				field.setAccessible(true);
				field.set(localDeliveryMailet, fileSystem);
			}
			
			initMethod.invoke(localDeliveryMailet, this.getMailetConfig());
		}
		catch (Exception e)
		{
			throw new MessagingException("Failed to initialize TimelyAndReliableLocalDelivery.", e);
		}
		
		notificationProducer = new ReliableDispatchedNotificationProducer(new NotificationSettings(true, "Local Direct Delivery Agent", "Your message was successfully dispatched."));
	}
	
	protected Field getDeclaredFieldQuietly(Class<?> clazz, String fieldName)
	{
		Field retVal = null;
		
		try
		{
			retVal = clazz.getDeclaredField(fieldName);
		}
		catch(Throwable t)
		{
			/* no-op... handled quietly */
		}
		
		return retVal;
	}
	
	protected Object createLocalDeliveryClass() throws Exception
	{
		Class<?> clazz = TimelyAndReliableLocalDelivery.class.getClassLoader().loadClass("org.apache.james.transport.mailets.LocalDelivery");
		return clazz.newInstance();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void service(Mail mail) throws MessagingException 
	{
		LOGGER.debug("Calling timely and reliable service method.");
		
		boolean deliverySuccessful = false;
		
		final MimeMessage msg = mail.getMessage();
		String originalMessageId = msg.getMessageID();
		final boolean isReliableAndTimely = TxUtil.isReliableAndTimelyRequested(msg);
		
		final NHINDAddressCollection recipients = getMailRecipients(mail);
								
		final NHINDAddress sender = getMailSender(mail);
		
		
		try
		{
			serviceMethod.invoke(localDeliveryMailet, mail);
			deliverySuccessful = true;
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to invoke service method.", e);
		}
		
		final Tx txToTrack = this.getTxToTrack(msg, sender, recipients);
		
		if (deliverySuccessful)
		{
			if (isReliableAndTimely && txToTrack.getMsgType() == TxMessageType.IMF)
			{

				// send back an MDN dispatched message
				final Collection<NotificationMessage> notifications = 
						notificationProducer.produce(new Message(msg), recipients.toInternetAddressCollection());
				if (notifications != null && notifications.size() > 0)
				{
					LOGGER.debug("Sending MDN \"dispatched\" messages");
					// create a message for each notification and put it on James "stack"
					for (NotificationMessage message : notifications)
					{
						try
						{
							message.saveChanges();
							
							if (dispatchedMDNDelay > 0)
								Thread.sleep(dispatchedMDNDelay);
							
							
							String mdnMessageId = message.getMessageID();
							Date sentDate = message.getSentDate();
							SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							String sentDateString = sdf.format(sentDate);
							getMailetContext().sendMail(message);
							
							try {					        
						        StringBuilder loggingOutput = new StringBuilder();
						        loggingOutput.append("\n    Message-Date: " + sentDate);
						        loggingOutput.append("\n    Message-ID: " + mdnMessageId);
						        loggingOutput.append("\n    " + message.getFromHeader());
						        loggingOutput.append("\n    " + message.getToHeader());
						        loggingOutput.append("\n    Original-Message-ID: " + originalMessageId);
						        
						        // We need to dig into the message content in order to get the MDN disposition.
						        Object messageContent = message.getContent();
					        	com.sun.mail.dsn.MultipartReport multipartReport = (com.sun.mail.dsn.MultipartReport)messageContent;
					        	int bodyPartCount = multipartReport.getCount();
					        	
					        	for (int i = 0; i < bodyPartCount; i++) {
					        		javax.mail.BodyPart bodyPart = multipartReport.getBodyPart(i);
					        		try {
						        		com.sun.mail.dsn.DispositionNotification myContent = (com.sun.mail.dsn.DispositionNotification)bodyPart.getContent();
						        		
						        		InternetHeaders internetHeaders = myContent.getNotifications();
						        		for (Enumeration<?> myHeaderLines = internetHeaders.getAllHeaderLines(); myHeaderLines.hasMoreElements();) {
						        			String potentialDisposition = myHeaderLines.nextElement().toString();
						        			int dispositionIndex = potentialDisposition.indexOf("Disposition");
						        			if (dispositionIndex != -1) {
						        				loggingOutput.append("\n    " + potentialDisposition);
						        				i = bodyPartCount + 50;  // Break from the for loop.
						        			}
										}
					        		} catch (Exception exe) {}
					        	}
					        	
								LOGGER.info(loggingOutput.toString());
								//LOGGER.info("Processed MDN " + mdnMessageId + " sent successfully in response to " + originalMessageId + " on " + sentDateString + "."); 
							} catch(Exception ex) {
								LOGGER.warn("Error logging MDN.");
								LOGGER.warn(ex.getMessage());
								LOGGER.warn(ex);
							}
							
						}
						///CLOVER:OFF
						catch (Throwable t)
						{
							// don't kill the process if this fails
							LOGGER.error("Error sending MDN dispatched message.", t);
						}
						///CLOVER:ON
					}
				}
			}
		}
		else
		{
			// create a DSN message regarless if timely and reliable was requested
			if (txToTrack != null && txToTrack.getMsgType() == TxMessageType.IMF)
				this.sendDSN(txToTrack, recipients, false);
		}
		
		LOGGER.debug("Exiting timely and reliable service method.");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Provider<DSNCreator> getDSNProvider() 
	{
		return new FailedDeliveryDSNCreatorProvider(this);
	}
}
