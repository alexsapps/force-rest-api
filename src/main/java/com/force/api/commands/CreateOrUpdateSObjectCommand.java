package com.force.api.commands;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

import com.force.api.CreateOrUpdateResult;
import com.force.api.ForceApi;
import com.force.api.exceptions.ApiException;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class CreateOrUpdateSObjectCommand extends ForceApiCommand<CreateOrUpdateResult> {
	
	public CreateOrUpdateSObjectCommand(String type, String externalIdField, String externalIdValue, Object sObject) {
		try{
			responseFormat(ResponseFormat.STREAM);
			relativeUri("/sobjects/"+type+"/"+externalIdField+"/"+URLEncoder.encode(externalIdValue,"UTF-8")+"?_HttpMethod=PATCH");
			method("POST");
			header("Accept", "application/json");
			header("Content-Type", "application/json");	
			content(ForceApi.getMapper().writeValueAsBytes(sObject));
		} catch (IOException e) {
			throw new SFApiException(e);
		}	
	}
	
	@Override
	public CreateOrUpdateResult interpret(HttpResponse res) {
		// See createSObject for note on streaming ambition
		InputStream is = null;
		try {
			is = res.getStream();
			if(res.getResponseCode()==201) {				
				return CreateOrUpdateResult.CREATED;
			} else if(res.getResponseCode()==204) {
				return CreateOrUpdateResult.UPDATED;
			} else {
				throw new ApiException(res.getResponseCode(), res.getString());
			}
		} finally {
			if (is !=null)
				try {
					is.close();
				} catch (IOException e) {
					throw new SFApiException(e);
				}
		}
	}
}