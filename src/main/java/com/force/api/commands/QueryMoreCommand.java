package com.force.api.commands;


public class QueryMoreCommand<T> extends QueryAnyCommand<T> {
	
	public QueryMoreCommand(String nextRecordsUrl, Class<T> clazz) {
		relativeToApiEndpointUri(nextRecordsUrl);
		this.clazz = clazz;
	}
	
}
