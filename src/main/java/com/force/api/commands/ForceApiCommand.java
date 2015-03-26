package com.force.api.commands;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest;
import com.force.api.http.HttpRequest.Header;
import com.force.api.http.HttpRequest.Parameter;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

/**
 * @author a.taylor (parts borrowed from HttpRequest)
 */
public class ForceApiCommand<T> implements IForceApiCommand<T>, ResponseInterpreter<T> {

	ResponseFormat responseFormat = ResponseFormat.STREAM;
	
	byte[] contentBytes;
	InputStream contentStream;

	List<Header> headers = new ArrayList<Header>();
	List<Parameter> parameters = new ArrayList<Parameter>();
	String method;
	String relativeUri;
	String relativeToApiEndpointUri;
	String absoluteUri;
	int expectedCode = -1; // -1 means no expected code specified.
	
	ResponseInterpreter<T> responseInterpreter;

	public ForceApiCommand<T> expectsCode(int value) {
		expectedCode = value;
		return this;
	}

	public ForceApiCommand<T> header(String key, String value) {
		headers.add(new Header(key,value));
		
		return this;
	}
	
	public ForceApiCommand<T> addAllHeaders(List<Header> headers) {
		headers.addAll(headers);
		
		return this;
	}
	
	public ForceApiCommand<T> responseFormat(ResponseFormat value) {
		responseFormat = value;
		return this;
	}
	
	public ForceApiCommand<T> relativeUri(String value) {
		if (absoluteUri != null)
			throw new IllegalStateException("Cannot add relative uri after absolute uri has been set with absoluteUri()");
		relativeUri = value;
		return this;
	}
	
	public  void relativeToApiEndpointUri(String relativeToApiEndpointUri) {
		this.relativeToApiEndpointUri = relativeToApiEndpointUri;
	}
	
	public ForceApiCommand<T> absoluteUri(String value) {
		if (relativeUri != null)
			throw new IllegalStateException("Cannot add absolute uri after relative uri has been set with relativeUri()");
		absoluteUri = value;
		return this;
	}
	
	public ForceApiCommand<T> method(String value) {
		method = value;
		return this;
	}
	
	public ForceApiCommand<T> content(byte[] value) {
		if(parameters.size()>0) {
			throw new IllegalStateException("Cannot add request content as byte[] after post parameters have been set with param() or preEncodedParam()");
		}
		contentBytes = value;
		return this;
	}

	public ForceApiCommand<T> param(String key, String value) {
		if(contentBytes!=null) {
			throw new IllegalStateException("Cannot add params to HttpRequest after content(byte[]) has been called with non-null value");
		}
		parameters.add(new Parameter(key, value));
		return this;
	}
	
	public ForceApiCommand<T> interpreter(ResponseInterpreter<T> responseInterpreter) {
		this.responseInterpreter = responseInterpreter;
		return this;
	}
	
	public String getMethod() {
		return method;
	}

	public String getRelativeUri() {
		return relativeUri;
	}

	public int getExpectedCode() {
		return expectedCode;
	}

	public List<Header> getHeaders() {
		return Collections.unmodifiableList(headers);
	}

	public List<Parameter> getParameters() {
		return Collections.unmodifiableList(parameters);
	}

	public byte[] getContent() {
		return contentBytes;
	}

	public ResponseFormat getResponseFormat() {
		return responseFormat;
	}	

	@Override
	public T interpretResult(HttpResponse response) throws SFApiException {
		T result = interpret(response);
		if (responseInterpreter != null)
			result = responseInterpreter.interpret(response);
		return result;
	}

	/**
	 * If subclassing, you can override this method.  Otherwise, you can
	 * call "interpreter" and pass in a lambda expression.
	 */
	@Override
	public /* virtual, not final */ T interpret(HttpResponse response) {
		return null;
	}

	@Override
	public HttpRequest toHttpRequest(String uriBase, String apiEndpoint) {
		return new HttpRequest(getResponseFormat())
			.method(getMethod())
			.content(getContent())
			.expectsCode(getExpectedCode())
			.url(relativeToApiEndpointUri != null ? apiEndpoint + relativeToApiEndpointUri : absoluteUri != null ? absoluteUri : uriBase + relativeUri)
			.addAllHeaders(getHeaders())
			.addAllParams(getParameters());
	}

}
