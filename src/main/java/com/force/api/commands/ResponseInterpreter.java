package com.force.api.commands;

import com.force.api.http.HttpResponse;

/**
 * @author a.taylor
 * @param <T>		The type of the result of interpreting the http response
 */
public interface ResponseInterpreter<T> {
	
	/**
	 * Interprets the HttpResponse for a particular command.  For example,
	 * an interpreter may convert http response codes into an enum specific
	 * to the command, or extract a single string value from a JSON response.
	 * 
	 * @param response
	 * @return 		a value that is more useful to the user than a raw HTTP response.
	 */
	public T interpret(HttpResponse response);
}
