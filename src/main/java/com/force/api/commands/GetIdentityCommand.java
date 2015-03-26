package com.force.api.commands;

import java.io.IOException;
import java.io.InputStream;

import com.force.api.ForceApi;
import com.force.api.Identity;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class GetIdentityCommand extends ForceApiCommand<Identity>{

	public GetIdentityCommand(String identityURI) {
		responseFormat(ResponseFormat.STREAM);
		absoluteUri(identityURI);
		method("GET");
		header("Accept", "application/json");
	}
	
	@Override
	public Identity interpret(HttpResponse response) {
		try (InputStream is=response.getStream()) {
			return ForceApi.getMapper().readValue(is, Identity.class);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
	
}
