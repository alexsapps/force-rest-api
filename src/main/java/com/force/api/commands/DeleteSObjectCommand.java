package com.force.api.commands;

import java.io.IOException;
import java.io.InputStream;

import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class DeleteSObjectCommand extends ForceApiCommand<Object> {
	
	public DeleteSObjectCommand(String type, String id) {
		responseFormat(ResponseFormat.STREAM);
		relativeUri("/sobjects/"+type+"/"+id);
		method("DELETE");
	}
	
	@Override
	public Object interpret(HttpResponse response) {
		try (InputStream is = response.getStream()) {
			//whee
			return null;
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
}
