package com.force.api.commands;

import java.util.Map;

@SuppressWarnings("rawtypes")
public class MapQueryMoreCommand extends QueryMoreCommand<Map> {
	public MapQueryMoreCommand(String nextRecordsUrl) {
		super(nextRecordsUrl, Map.class);
	}
}
