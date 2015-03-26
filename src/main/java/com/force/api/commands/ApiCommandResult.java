package com.force.api.commands;

import com.force.api.http.HttpResponse;

/**
 * @author a.taylor
 */
public class ApiCommandResult<T> {
	
	private HttpResponse response;
	private T result;
	
	public ApiCommandResult(HttpResponse response, T result) {
		super();
		this.response = response;
		this.result = result;
	}
	
	public HttpResponse getResponse() {
		return response;
	}
	
	public T getResult() {
		return result;
	}
}
