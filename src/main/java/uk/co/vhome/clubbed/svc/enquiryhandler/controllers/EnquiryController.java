package uk.co.vhome.clubbed.svc.enquiryhandler.controllers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import uk.co.vhome.clubbed.svc.security.MD5Helper;
import uk.co.vhome.clubbed.svc.enquiryhandler.model.commands.AcceptFreeTokenCommand;
import uk.co.vhome.clubbed.svc.enquiryhandler.model.commands.NewClubEnquiryCommand;
import uk.co.vhome.clubbed.svc.enquiryhandler.repositories.NonAxonEntityRepository;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.util.Collections;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;

@RestController
@Validated
@RequestMapping("/v2")
public class EnquiryController
{
	private static final Log LOGGER = LogFactory.getLog(EnquiryController.class);

	private final CommandBus commandBus;

	private final NonAxonEntityRepository enquiryRepository;

	public EnquiryController(CommandBus commandBus, NonAxonEntityRepository enquiryRepository)
	{
		this.commandBus = commandBus;
		this.enquiryRepository = enquiryRepository;
	}

	@GetMapping(path = "/token-claim/emails/{email}")
	public DeferredResult<ResponseEntity<String>> tokenClaim(@PathVariable @Valid @NotBlank @Email String email)
	{
		String enquiryId = MD5Helper.hash(email);

		LOGGER.info(enquiryId + " accepting free token");

		AcceptFreeTokenCommand acceptFreeTokenCommand = new AcceptFreeTokenCommand(enquiryId, email);

		DeferredResult<ResponseEntity<String>> handlerResult = new DeferredResult<>();

		commandBus.dispatch(asCommandMessage(acceptFreeTokenCommand), tokenClaimCommandCallback(enquiryId, handlerResult));

		return handlerResult;
	}

	@PostMapping(path = "/club-enquiry/emails/{email}")
	public DeferredResult<ResponseEntity<Object>> register(@PathVariable @Valid @NotBlank @Email String email,
	                                                       @Valid UserDetail userInfo)
	{

		DeferredResult<ResponseEntity<Object>> handlerResult = new DeferredResult<>();

		String enquiryId = MD5Helper.hash(email);

		if (enquiryRepository.existsById(enquiryId))
		{
			ApiError apiError = new ApiError("email", "Address already registered");
			ResponseEntity<Object> body = ResponseEntity.badRequest().body(Collections.singleton(apiError));
			handlerResult.setResult(body);
			return handlerResult;
		}

		String phoneNumberOrNull = userInfo.getPhone().isEmpty() ? null : userInfo.getPhone();

		NewClubEnquiryCommand newClubEnquiryCommand = new NewClubEnquiryCommand(email,
		                                                                        userInfo.getFirstName(),
		                                                                        userInfo.getLastName(),
		                                                                        phoneNumberOrNull);

		commandBus.dispatch(asCommandMessage(newClubEnquiryCommand), newEnquiryCommandCallback(enquiryId, handlerResult));

		return handlerResult;
	}

	private CommandCallback<Object, Object> tokenClaimCommandCallback(String enquiryId, DeferredResult<ResponseEntity<String>> handlerResult)
	{
		return new CommandCallback<>()
		{
			@Override
			public void onSuccess(CommandMessage<?> commandMessage, Object result)
			{
				handlerResult.setResult(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).header(HttpHeaders.LOCATION, "/token-claim-ok").build());
				LOGGER.info( "Successfully claimed token for " + enquiryId);
			}

			@Override
			public void onFailure(CommandMessage<?> commandMessage, Throwable cause)
			{
				handlerResult.setErrorResult(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).header(HttpHeaders.LOCATION, "/token-claim-failed").build());
				LOGGER.error( "Unsuccessfully claimed token for " + enquiryId, cause);
			}
		};
	}

	private CommandCallback<Object, Object> newEnquiryCommandCallback(String enquiryId, DeferredResult<ResponseEntity<Object>> handlerResult)
	{
		return new CommandCallback<>()
		{
			@Override
			public void onSuccess(CommandMessage<?> commandMessage, Object result)
			{
				handlerResult.setResult(ResponseEntity.ok().build());
				LOGGER.info("Registered enquiry for: " + enquiryId);
			}

			@Override
			public void onFailure(CommandMessage<?> commandMessage, Throwable cause)
			{
				handlerResult.setErrorResult(ResponseEntity.badRequest().body(cause.getMessage()));
				LOGGER.error("Failed to register enquiry", cause);
			}
		};
	}

}