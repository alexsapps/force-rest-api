package com.force.api.commands;

import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest;
import com.force.api.http.HttpResponse;

/**
 * @author a.taylor
 */
public interface IForceApiCommand<T> {	
	/**
	 * Returns the URI of the command relative to the base URI.
	 */
	
	public HttpRequest toHttpRequest(String uriBase, String apiEndpoint);

	public T interpretResult(HttpResponse apiRequest) throws SFApiException;
	
}
