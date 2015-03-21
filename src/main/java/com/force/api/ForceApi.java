package com.force.api;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.force.api.exceptions.ApiException;
import com.force.api.exceptions.AuthenticationFailedException;
import com.force.api.exceptions.RefreshFailedApiException;
import com.force.api.exceptions.SFApiException;
import com.force.api.exceptions.SObjectException;
import com.force.api.http.Http;
import com.force.api.http.HttpRequest;
import com.force.api.http.HttpResponse;
import com.force.api.http.HttpRequest.ResponseFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * main class for making API calls.
 *
 * This class is cheap to instantiate and throw away. It holds a user's session
 * as state and thus should never be reused across multiple user sessions,
 * unless that's explicitly what you want to do.
 *
 * For web apps, you should instantiate this class on every request and feed it
 * the session information as obtained from a session cookie or similar. An
 * exception to this rule is if you make all API calls as a single API user.
 * Then you can keep a static reference to this class.
 *
 * @author jjoergensen
 *
 */
public class ForceApi {
	private static final Logger log = LoggerFactory.getLogger(ForceApi.class);
	private static final ObjectMapper jsonMapper;

	static {
		jsonMapper = new ObjectMapper();
		jsonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		jsonMapper.setSerializationInclusion(Include.NON_NULL);
	}

	public static ObjectMapper getMapper() { return jsonMapper; }
	final private ApiConfig config;
	private ApiSession session;
	private boolean autoRenew = false;
	private TokenRenewalObserver observer=null; 
	private boolean gzip=false;
	public ForceApi(ApiConfig config, ApiSession session) {
		this.config = config;
		this.setSession(session);
		if(session.getRefreshToken()!=null) {
			autoRenew = true;
		}
	}

	public ForceApi(ApiSession session) {
		this(new ApiConfig(), session);
	}

	public ForceApi(ApiConfig apiConfig) {
		config = apiConfig;
		setSession(Auth.authenticate(apiConfig));
		autoRenew  = true;

	}
	
	public void setGzip(boolean gzip) {
		this.gzip=gzip;
	}

	public Identity getIdentity() {
		try (InputStream is=
				apiRequest(new HttpRequest(ResponseFormat.STREAM)
				.url(uriBase())
				.method("GET")
				.header("Accept", "application/json")
				)
				.getStream()) {
			@SuppressWarnings("unchecked")
			Map<String,Object> resp = jsonMapper.readValue(
					is,Map.class);
			log.debug("ID="+((String) resp.get("identity")));
			return getIdentity((String) resp.get("identity"));
		} catch (JsonParseException e) {
			throw new SFApiException(e);
		} catch (JsonMappingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		} 
	}

	public Identity getIdentity(String identityURL) {
		try (InputStream is=
				apiRequest(new HttpRequest(ResponseFormat.STREAM)
				.url(identityURL)
				.method("GET")
				.header("Accept", "application/json")
			).getStream() )  {
			return jsonMapper.readValue(
					is, Identity.class);
		} catch (JsonParseException e) {
			throw new SFApiException(e);
		} catch (JsonMappingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		}

	}

	public ResourceRepresentation getSObject(String type, String id) throws SFApiException {
		// Should we return null or throw an exception if the record is not found?
		// Right now will just throw crazy runtimeexception with no explanation
		return new ResourceRepresentation(apiRequest(new HttpRequest(ResponseFormat.STREAM)
					.url(uriBase()+"/sobjects/"+type+"/"+id)
					.method("GET")
					.header("Accept", "application/json")));
	}

	public String createSObject(String type, Object sObject) {
		try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
					.url(uriBase()+"/sobjects/"+type)
					.method("POST")
					.header("Accept", "application/json")
					.header("Content-Type", "application/json")
					.expectsCode(201)
					.content(jsonMapper.writeValueAsBytes(sObject))).getStream()){
			// We're trying to keep Http classes clean with no reference to JSON/Jackson
			// Therefore, we serialize to bytes before we pass object to HttpRequest().
			// But it would be nice to have a streaming implementation. We can do that
			// by using ObjectMapper.writeValue() passing in output stream, but then we have
			// polluted the Http layer.
			CreateResponse result = jsonMapper.readValue(is,CreateResponse.class);

			if (result.isSuccess()) {
				return (result.getId());
			} else {
				throw new SObjectException(result.getErrors());
			}
		} catch (JsonGenerationException e) {
			throw new SFApiException(e);
		} catch (JsonMappingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

	public void updateSObject(String type, String id, Object sObject) {
		try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
				.url(uriBase()+"/sobjects/"+type+"/"+id+"?_HttpMethod=PATCH")
				.method("POST")
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.expectsCode(204)
				.content(jsonMapper.writeValueAsBytes(sObject))
			).getStream()){
			// See createSObject for note on streaming ambition
		} catch (JsonGenerationException e) {
			throw new SFApiException(e);
		} catch (JsonMappingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

	public void deleteSObject(String type, String id) {
		try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
			.url(uriBase()+"/sobjects/"+type+"/"+id)
			.method("DELETE")).getStream()
		) {
			// whee
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

	public CreateOrUpdateResult createOrUpdateSObject(String type, String externalIdField, String externalIdValue, Object sObject) {
		InputStream is=null;
		try {
			// See createSObject for note on streaming ambition
			HttpResponse res =
				apiRequest(new HttpRequest(ResponseFormat.STREAM)
					.url(uriBase()+"/sobjects/"+type+"/"+externalIdField+"/"+URLEncoder.encode(externalIdValue,"UTF-8")+"?_HttpMethod=PATCH")
					.method("POST")
					.header("Accept", "application/json")
					.header("Content-Type", "application/json")
					.content(jsonMapper.writeValueAsBytes(sObject))
				);			
			is=res.getStream();
			if(res.getResponseCode()==201) {				
				return CreateOrUpdateResult.CREATED;
			} else if(res.getResponseCode()==204) {
				return CreateOrUpdateResult.UPDATED;
			} else {
				throw new ApiException(res.getResponseCode(), res.getString());
//				System.out.println("Code: "+res.getResponseCode());
	//			System.out.println("Message: "+res.getString());
		//		throw new RuntimeException();
			} 
		} catch (JsonGenerationException e) {
			throw new SFApiException(e);
		} catch (JsonMappingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		} finally {
			if (is !=null)
				try {
					is.close();
				} catch (IOException e) {
					throw new SFApiException(e);
				}
		}

	}

	public <T> QueryResult<T> query(String query, Class<T> clazz) {
        try {
            return queryAny(uriBase() + "/query/?q=" + URLEncoder.encode(query, "UTF-8"), clazz);
        } catch (UnsupportedEncodingException e) {
            throw new SFApiException(e);
        }
    }

	@SuppressWarnings("rawtypes")
	public QueryResult<Map> query(String query) {
		return query(query, Map.class);
	}

    public <T> QueryResult<T> queryMore(String nextRecordsUrl, Class<T> clazz) {
        return queryAny(getSession().getApiEndpoint() + nextRecordsUrl, clazz);
    }

    @SuppressWarnings("rawtypes")
	public QueryResult<Map> queryMore(String nextRecordsUrl) {
        return queryMore(nextRecordsUrl, Map.class);
    }

    private <T> QueryResult<T> queryAny(String queryUrl, Class<T> clazz) {
        try (InputStream is= apiRequest(new HttpRequest(ResponseFormat.STREAM)
                    .url(queryUrl)
                    .method("GET")
                    .header("Accept", "application/json")
                    .expectsCode(200)).getStream()){
            
            // We build the result manually, because we can't pass the type information easily into
            // the JSON parser mechanism.

            QueryResult<T> result = new QueryResult<T>();
            JsonNode root = jsonMapper.readTree(is);
            result.setDone(root.get("done").booleanValue());
            result.setTotalSize(root.get("totalSize").intValue());
            if (root.get("nextRecordsUrl") != null) {
                result.setNextRecordsUrl(root.get("nextRecordsUrl").textValue());
            }
            List<T> records = new ArrayList<T>();
            for (JsonNode elem : root.get("records")) {
                records.add(jsonMapper.readValue(
                		jsonMapper.treeAsTokens(normalizeCompositeResponse(elem)), clazz));
            }
            result.setRecords(records);
            return result;
        } catch (JsonParseException e) {
            throw new SFApiException(e);
        } catch (JsonMappingException e) {
            throw new SFApiException(e);
        } catch (IOException e) {
            throw new SFApiException(e);
        }
    }

    public DescribeGlobal describeGlobal() {
		try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
					.url(uriBase()+"/sobjects/")
					.method("GET")
					.header("Accept", "application/json")).getStream()){
			return jsonMapper.readValue(is,DescribeGlobal.class);
		} catch (JsonParseException e) {
			throw new SFApiException(e);
		} catch (JsonMappingException e) {
			throw new SFApiException(e);
		} catch (UnsupportedEncodingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

    public <T> DiscoverSObject<T> discoverSObject(String sobject, Class<T> clazz) {
        try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
                    .url(uriBase() + "/sobjects/" + sobject)
                    .method("GET")
                    .header("Accept", "application/json")
                    .expectsCode(200)).getStream()){

            final JsonNode root = jsonMapper.readTree(is);
            final DescribeSObjectBasic describeSObjectBasic = jsonMapper.readValue(
            		jsonMapper.treeAsTokens(root.get("objectDescribe")), DescribeSObjectBasic.class);
            final List<T> recentItems = new ArrayList<T>();
            for(JsonNode item : root.get("recentItems")) {
                recentItems.add(jsonMapper.readValue(
                		jsonMapper.treeAsTokens(item), clazz));
            }
            return new DiscoverSObject<T>(describeSObjectBasic, recentItems);
        } catch (JsonParseException e) {
            throw new SFApiException(e);
        } catch (JsonMappingException e) {
            throw new SFApiException(e);
        } catch (IOException e) {
            throw new SFApiException(e);
        }
    }

	public DescribeSObject describeSObject(String sobject) {
		try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
					.url(uriBase()+"/sobjects/"+sobject+"/describe")
					.method("GET")
					.header("Accept", "application/json")).getStream()) {
			return jsonMapper.readValue(is,DescribeSObject.class);
		} catch (JsonParseException e) {
			throw new SFApiException(e);
		} catch (JsonMappingException e) {
			throw new SFApiException(e);
		} catch (UnsupportedEncodingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
	
	public final String uriBase() {
		return(getSession().getApiEndpoint()+"/services/data/"+config.getApiVersion());
	}
	
	public final HttpResponse customApiRequest(HttpRequest req) {
		return apiRequest(req);
	}
	
	private final HttpResponse apiRequest(HttpRequest req) {
		
		req.setAuthorization("OAuth "+getSession().getAccessToken());
		req=req.gzip(gzip);              
		HttpResponse res = Http.send(req);
		if(res.getResponseCode()==401) {
			// Perform one attempt to auto renew session if possible
			if(autoRenew) {
				log.debug("Session expired. Refreshing session...");
				if (this.observer !=null) this.observer.tokenNeedsRenewal(getSession().getAccessToken(), getSession().getRefreshToken());
				if(getSession().getRefreshToken()!=null) {
					try {
						setSession(Auth.refreshOauthTokenFlow(config, getSession().getRefreshToken()));
						if (this.observer !=null) this.observer.tokenRenewedSuccessfully(session);
					} catch (RuntimeException e) {
						if (this.observer !=null) this.observer.tokenNotRenewedSuccessfully();
						throw e;
					}
				} else {
					setSession(Auth.authenticate(config));
				}
				req.setAuthorization("OAuth "+getSession().getAccessToken());
				res = Http.send(req);
				if (res.getResponseCode()==401) throw new RefreshFailedApiException(401,"Tried to refresh but failed.");
			} else {
				if (this.observer !=null) this.observer.tokenNotRenewedSuccessfully();
				throw new AuthenticationFailedException(401,"No refresh token, and 401 found");
			}
			
		}
		if(res.getResponseCode()>299) {
			if(res.getResponseCode()==401) {
				throw new AuthenticationFailedException(401,res.getString());
			} else {
				throw new ApiException(res.getResponseCode(), res.getString());
			}
		} else if(req.getExpectedCode()!=-1 && res.getResponseCode()!=req.getExpectedCode()) {
			throw new ApiException("Unexpected response from Force API. Got response code "+res.getResponseCode()+
					". Was expecting "+req.getExpectedCode()+" "+res.getString());
		} else {
			return res;
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

	public ApiConfig getConfig() {
		return this.config;
	}

	public ApiSession getSession() {
		return session;
	}

	public void setSession(ApiSession session) {
		this.session = session;
	}

	public TokenRenewalObserver getTokenRenewalObserver() {
		return this.observer;
	}

	public void setTokenRenewalObserver(TokenRenewalObserver observer) {
		this.observer = observer;
	}
}
