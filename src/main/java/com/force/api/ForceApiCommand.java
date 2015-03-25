package com.force.api;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.force.api.http.HttpRequest.Header;
import com.force.api.http.HttpRequest.Parameter;
import com.force.api.http.HttpRequest.ResponseFormat;

/**
 * @author a.taylor (parts borrowed from HttpRequest)
 */
public class ForceApiCommand implements IForceApiCommand {

	ResponseFormat responseFormat = ResponseFormat.STREAM;
	
	byte[] contentBytes;
	InputStream contentStream;

	List<Header> headers = new ArrayList<Header>();
	List<Parameter> parameters = new ArrayList<Parameter>();
	String method;
	String relativeUri;
	int expectedCode = -1; // -1 means no expected code specified.

	public ForceApiCommand expectsCode(int value) {
		expectedCode = value;
		return this;
	}

	public ForceApiCommand header(String key, String value) {
		headers.add(new Header(key,value));
		
		return this;
	}
	
	public ForceApiCommand addAllHeaders(List<Header> headers) {
		headers.addAll(headers);
		
		return this;
	}
	
	public ForceApiCommand responseFormat(ResponseFormat value) {
		responseFormat = value;
		return this;
	}
	
	public ForceApiCommand relativeUri(String value) {
		relativeUri = value;
		return this;
	}
	
	public ForceApiCommand method(String value) {
		method = value;
		return this;
	}
	
	public ForceApiCommand content(byte[] value) {
		if(parameters.size()>0) {
			throw new IllegalStateException("Cannot add request content as byte[] after post parameters have been set with param() or preEncodedParam()");
		}
		contentBytes = value;
		return this;
	}

	public ForceApiCommand param(String key, String value) {
		if(contentBytes!=null) {
			throw new IllegalStateException("Cannot add params to HttpRequest after content(byte[]) has been called with non-null value");
		}
		parameters.add(new Parameter(key, value));
		return this;
	}
	
	@Override
	public String getMethod() {
		return method;
	}

	@Override
	public String getRelativeUri() {
		return relativeUri;
	}

	@Override
	public int getExpectedCode() {
		return expectedCode;
	}

	@Override
	public List<Header> getHeaders() {
		return Collections.unmodifiableList(headers);
	}

	@Override
	public List<Parameter> getParameters() {
		return Collections.unmodifiableList(parameters);
	}

	@Override
	public byte[] getContent() {
		return contentBytes;
	}

	@Override
	public ResponseFormat getResponseFormat() {
		return responseFormat;
	}

}
