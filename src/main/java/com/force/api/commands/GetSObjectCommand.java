package com.force.api.commands;

import com.force.api.ResourceRepresentation;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class GetSObjectCommand extends ForceApiCommand<ResourceRepresentation> {

	public GetSObjectCommand(String type, String id) {
		responseFormat(ResponseFormat.STREAM);
		relativeUri("/sobjects/"+type+"/"+id);
		method("GET");
		header("Accept", "application/json");
	}
	
	@Override
	public ResourceRepresentation interpret(HttpResponse response) {
		// Should we return null or throw an exception if the record is not found?
		// Right now will just throw crazy runtimeexception with no explanation
		return new ResourceRepresentation(response);
	}
	
}
