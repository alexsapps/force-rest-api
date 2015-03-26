package com.force.api.commands;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.force.api.exceptions.SFApiException;

public class QueryCommand<T> extends QueryAnyCommand<T> {
	
	public QueryCommand(String query, Class<T> clazz) {
		try {
			relativeUri("/query/?q=" + URLEncoder.encode(query, "UTF-8"));
			this.clazz = clazz;
        } catch (UnsupportedEncodingException e) {
            throw new SFApiException(e);
        }
	}
	
}
