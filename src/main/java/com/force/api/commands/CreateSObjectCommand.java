package com.force.api.commands;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.force.api.CreateResponse;
import com.force.api.ForceApi;
import com.force.api.exceptions.SFApiException;
import com.force.api.exceptions.SObjectException;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class CreateSObjectCommand extends ForceApiCommand<String> {

	public CreateSObjectCommand(String type, Object sObject) {
		this(type, sObject, ForceApi.getMapper());
	}
	
	public CreateSObjectCommand(String type, Object sObject, ObjectMapper mapper) {
		try {
			responseFormat(ResponseFormat.STREAM);
			relativeUri("/sobjects/"+type);
			method("POST");
			header("Accept", "application/json");
			header("Content-Type", "application/json");
			expectsCode(201);
			content(mapper.writeValueAsBytes(sObject));
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

	@Override
	public String interpret(HttpResponse response) {
		try (InputStream is=response.getStream()){
			// We're trying to keep Http classes clean with no reference to JSON/Jackson
			// Therefore, we serialize to bytes before we pass object to HttpRequest().
			// But it would be nice to have a streaming implementation. We can do that
			// by using ObjectMapper.writeValue() passing in output stream, but then we have
			// polluted the Http layer.
			CreateResponse result = ForceApi.getMapper().readValue(is,CreateResponse.class);

			if (result.isSuccess()) {
				return (result.getId());
			} else {
				throw new SObjectException(result.getErrors());
			}
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
}
