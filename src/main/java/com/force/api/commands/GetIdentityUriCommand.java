package com.force.api.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.force.api.ForceApi;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class GetIdentityUriCommand extends ForceApiCommand<String> {
	
	private static final Logger log = LoggerFactory.getLogger(GetIdentityUriCommand.class);
	
	public GetIdentityUriCommand() {
		responseFormat(ResponseFormat.STREAM);
		relativeUri("");
		method("GET");
		header("Accept", "application/json");
	}
	
	@Override
	public String interpret(HttpResponse response) {
		try (InputStream is= response.getStream()) {
			@SuppressWarnings("unchecked")
			Map<String,Object> resp = ForceApi.getMapper().readValue(
					is,Map.class);
			
			log.debug("ID="+((String) resp.get("identity")));
			
			return (String) resp.get("identity");
		} catch (IOException e) {
			throw new SFApiException(e);
		}	
	}
	
}
