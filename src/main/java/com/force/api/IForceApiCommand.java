package com.force.api;

import java.util.List;

import com.force.api.http.HttpRequest.Header;
import com.force.api.http.HttpRequest.Parameter;
import com.force.api.http.HttpRequest.ResponseFormat;

/**
 * @author a.taylor
 */
public interface IForceApiCommand {
	
	public String getMethod();
	
	/**
	 * Returns the URI of the command relative to the base URI.
	 */
	public String getRelativeUri();
	
	public int getExpectedCode();
	
	public List<Header> getHeaders();
	
	public List<Parameter> getParameters();
	
	public byte[] getContent();
	
	public ResponseFormat getResponseFormat();
	
}
