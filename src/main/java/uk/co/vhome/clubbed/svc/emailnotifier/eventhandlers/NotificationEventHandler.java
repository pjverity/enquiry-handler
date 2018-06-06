package uk.co.vhome.clubbed.svc.emailnotifier.eventhandlers;

import freemarker.template.TemplateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import uk.co.vhome.clubbed.svc.emailnotifier.mail.AdminNotificationMailMessageBuilder;
import uk.co.vhome.clubbed.svc.emailnotifier.mail.EnquiryResponseMailMessageBuilder;
import uk.co.vhome.clubbed.svc.emailnotifier.mail.MailMessageSender;
import uk.co.vhome.clubbed.svc.common.events.ClubEnquiryCreatedEvent;
import uk.co.vhome.clubbed.svc.common.events.FreeTokenAcceptedEvent;

import javax.mail.MessagingException;
import java.io.IOException;

@Component
@ProcessingGroup("enquiry")
public class NotificationEventHandler
{
	private static final Logger LOGGER = LogManager.getLogger();

	private static final String CLUB_ENQUIRY = "club_enquiry";

	private static final String ADMIN_NOTIFICATION = "admin_notification";

	private final String domainEmailAddress;

	private final String domainEmailName;

	public NotificationEventHandler(@Value("${email-notifier.domainEmailAddress}") String domainEmailAddress,
	                                @Value("${email-notifier.domainEmailName}") String domainEmailName)
	{
		this.domainEmailAddress = domainEmailAddress;
		this.domainEmailName = domainEmailName;
	}

	@EventHandler
	void on(ClubEnquiryCreatedEvent clubEnquiryCreatedEvent,
	        EnquiryResponseMailMessageBuilder enquiryResponseMailMessageBuilder,
	        AdminNotificationMailMessageBuilder adminNotificationMailMessageBuilder,
	        MailMessageSender mailMessageSender)
	{
		try
		{
			String messageContent = enquiryResponseMailMessageBuilder.build(CLUB_ENQUIRY,
			                                                                clubEnquiryCreatedEvent.getEmailAddress(),
			                                                                clubEnquiryCreatedEvent.getFirstName());

			mailMessageSender.send("Thanks for your enquiry",
			                       messageContent,
			                       clubEnquiryCreatedEvent.getEmailAddress(),
			                       domainEmailAddress, domainEmailName);

			LOGGER.info("Notified: {}", clubEnquiryCreatedEvent.getEmailAddress());

			messageContent = adminNotificationMailMessageBuilder.build(ADMIN_NOTIFICATION,
			                                                           "New Registration",
			                                                           clubEnquiryCreatedEvent.getEmailAddress(),
			                                                           clubEnquiryCreatedEvent.getFirstName(),
			                                                           clubEnquiryCreatedEvent.getLastName(),
			                                                           clubEnquiryCreatedEvent.getPhoneNumber());

			mailMessageSender.send("New Registration", messageContent, domainEmailAddress, domainEmailAddress, domainEmailName);

		}
		catch (TemplateException | IOException e)
		{
			LOGGER.error("Failed to generate message content for email notification", e);
		}
		catch (MessagingException | MailException e)
		{
			LOGGER.error("Failed to send mail to " + clubEnquiryCreatedEvent.getEmailAddress(), e);
		}
	}

	@EventHandler
	void on(FreeTokenAcceptedEvent freeTokenAcceptedEvent,
	        AdminNotificationMailMessageBuilder adminNotificationMailMessageBuilder,
	        MailMessageSender mailMessageSender)
	{
		LOGGER.info("Notified: {} free token claimed", domainEmailAddress);

		try
		{
			String messageContent = adminNotificationMailMessageBuilder.build(ADMIN_NOTIFICATION,
			                                                           "Free Token Claimed",
			                                                           freeTokenAcceptedEvent.getEmailAddress(),
			                                                           null,
			                                                           null,
			                                                           null
			);

			mailMessageSender.send("Free token claimed", messageContent,
			                       domainEmailAddress,
			                       domainEmailAddress, domainEmailName);

		}
		catch (TemplateException | IOException e)
		{
			LOGGER.error("Failed to generate message content for email notification", e);
		}
		catch (MessagingException | MailException e)
		{
			LOGGER.error("Failed to send mail to " + freeTokenAcceptedEvent.getEmailAddress(), e);
		}
	}
}