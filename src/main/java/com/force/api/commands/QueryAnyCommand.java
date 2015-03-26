package com.force.api.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.force.api.ForceApi;
import com.force.api.QueryResult;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

public class QueryAnyCommand<S> extends ForceApiCommand<QueryResult<S>> {

	protected Class<S> clazz;
	
	public QueryAnyCommand() {
		responseFormat(ResponseFormat.STREAM);    
        method("GET");
        header("Accept", "application/json");
        expectsCode(200);
	}
	
	public QueryAnyCommand(String queryUrl, Class<S> clazz) {
		this();
		
		relativeUri(queryUrl);
		this.clazz=clazz;
	}

	@Override
	public QueryResult<S> interpret(HttpResponse response) {
		try (InputStream is = response.getStream()) {
			// We build the result manually, because we can't pass the type
			// information easily into
			// the JSON parser mechanism.

			QueryResult<S> result = new QueryResult<S>();
			JsonNode root = ForceApi.getMapper().readTree(is);
			result.setDone(root.get("done").booleanValue());
			result.setTotalSize(root.get("totalSize").intValue());
			if (root.get("nextRecordsUrl") != null) {
				result.setNextRecordsUrl(root.get("nextRecordsUrl").textValue());
			}
			List<S> records = new ArrayList<S>();
			for (JsonNode elem : root.get("records")) {
				records.add(ForceApi.getMapper().readValue(
						ForceApi.getMapper().treeAsTokens(
								normalizeCompositeResponse(elem)), clazz));
			}
			result.setRecords(records);
			return result;
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
	
	/**
	 * Normalizes the JSON response in case it contains responses from
	 * Relationsip queries. For e.g.
	 * 
	 * <code>
	 * Query:
	 *   select Id,Name,(select Id,Email,FirstName from Contacts) from Account
	 *   
	 * Json Response Returned:
	 * 
	 * {
	 *	  "totalSize" : 1,
	 *	  "done" : true,
	 *	  "records" : [ {
	 *	    "attributes" : {
	 *	      "type" : "Account",
	 *	      "url" : "/services/data/v24.0/sobjects/Account/0017000000TcinJAAR"
	 *	    },
	 *	    "Id" : "0017000000TcinJAAR",
	 *	    "Name" : "test_acc_04_01",
	 *	    "Contacts" : {
	 *	      "totalSize" : 1,
	 *	      "done" : true,
	 *	      "records" : [ {
	 *	        "attributes" : {
	 *	          "type" : "Contact",
	 *	          "url" : "/services/data/v24.0/sobjects/Contact/0037000000zcgHwAAI"
	 *	        },
	 *	        "Id" : "0037000000zcgHwAAI",
	 *	        "Email" : "contact@email.com",
	 *	        "FirstName" : "John"
	 *	      } ]
	 *	    }
	 *	  } ]
	 *	}
	 * </code>
	 * 
	 * Will get normalized to:
	 * 
	 * <code>
	 * {
	 *	  "totalSize" : 1,
	 *	  "done" : true,
	 *	  "records" : [ {
	 *	    "attributes" : {
	 *	      "type" : "Account",
	 *	      "url" : "/services/data/v24.0/sobjects/Account/accountId"
	 *	    },
	 *	    "Id" : "accountId",
	 *	    "Name" : "test_acc_04_01",
	 *	    "Contacts" : [ {
	 *	        "attributes" : {
	 *	          "type" : "Contact",
	 *	          "url" : "/services/data/v24.0/sobjects/Contact/contactId"
	 *	        },
	 *	        "Id" : "contactId",
	 *	        "Email" : "contact@email.com",
	 *	        "FirstName" : "John"
	 *	    } ]
	 *	  } ]
	 *	} 
	 * </code
	 * 
	 * This allows Jackson to deserialize the response into it's corresponding Object representation
	 * 
	 * @param node 
	 * @return
	 */
	private final JsonNode normalizeCompositeResponse(JsonNode node){
		Iterator<Entry<String, JsonNode>> elements = node.fields();
		ObjectNode newNode = JsonNodeFactory.instance.objectNode();
		Entry<String, JsonNode> currNode;
		while(elements.hasNext()){
			currNode = elements.next();

			newNode.put(currNode.getKey(), 
						(		currNode.getValue().isObject() && 
								currNode.getValue().get("records")!=null
						)?
								currNode.getValue().get("records"):
									currNode.getValue()
					);
		}
		return newNode;
		
	}
}
