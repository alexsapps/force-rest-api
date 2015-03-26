package com.force.api.commands;

import java.util.Map;

@SuppressWarnings("rawtypes")
public class MapQueryCommand extends QueryCommand<Map> {
	public MapQueryCommand(String query) {
		super(query, Map.class);
	}
}
