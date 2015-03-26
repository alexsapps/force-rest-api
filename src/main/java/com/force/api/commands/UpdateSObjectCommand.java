package com.force.api.commands;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.force.api.ForceApi;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class UpdateSObjectCommand extends ForceApiCommand<Object> {
	
	public UpdateSObjectCommand(String type, String id, Object sObject) {
		this(type, id, sObject, ForceApi.getMapper());
	}
	
	public UpdateSObjectCommand(String type, String id, Object sObject, ObjectMapper mapper) {
		try {
			responseFormat(ResponseFormat.STREAM);
			relativeUri("/sobjects/"+type+"/"+id+"?_HttpMethod=PATCH");
			method("POST");
			header("Accept", "application/json");
			header("Content-Type", "application/json");
			expectsCode(204);
			content(mapper.writeValueAsBytes(sObject));
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
	
	@Override
	public Object interpret(HttpResponse response) {
		try (InputStream is=response.getStream()){
			// See createSObject for note on streaming ambition
			return null;
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
}
