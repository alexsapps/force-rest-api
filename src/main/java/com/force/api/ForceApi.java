package com.force.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.force.api.commands.ApiCommandResult;
import com.force.api.commands.CreateOrUpdateSObjectCommand;
import com.force.api.commands.CreateSObjectCommand;
import com.force.api.commands.DeleteSObjectCommand;
import com.force.api.commands.GetIdentityCommand;
import com.force.api.commands.GetIdentityUriCommand;
import com.force.api.commands.GetSObjectCommand;
import com.force.api.commands.IForceApiCommand;
import com.force.api.commands.MapQueryCommand;
import com.force.api.commands.MapQueryMoreCommand;
import com.force.api.commands.QueryCommand;
import com.force.api.commands.QueryMoreCommand;
import com.force.api.commands.UpdateSObjectCommand;
import com.force.api.exceptions.ApiException;
import com.force.api.exceptions.AuthenticationFailedException;
import com.force.api.exceptions.RefreshFailedApiException;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.Http;
import com.force.api.http.HttpRequest;
import com.force.api.http.HttpRequest.ResponseFormat;
import com.force.api.http.HttpResponse;

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
	
	private static boolean debug;
	public static boolean isDebugMode() { return debug; }
	public static void setDebugMode() { debug = true; }
	
	static {
		jsonMapper = new ObjectMapper();
		jsonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		jsonMapper.setSerializationInclusion(Include.NON_NULL);
	}

	public static ObjectMapper getMapper() { return jsonMapper; }
	
	final private ApiConfig config;
	private ApiSession session;
	private boolean autoRenew = false;
	private TokenRenewalObserver observer = null; 
	private boolean gzip = false;
	
	public ForceApi(ApiConfig config, ApiSession session) {
		this(config,session,true);
	}
	
	public ForceApi(ApiConfig config, ApiSession session, boolean autoRenew) {
		this.config = config;
		this.setSession(session);
		if(session.getRefreshToken()!=null) {
			this.autoRenew = autoRenew;
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
		String identityUri = execute(new GetIdentityUriCommand());
		return getIdentity(identityUri);
	}
	public Identity getIdentity(String identityURL) {
		return execute(new GetIdentityCommand(identityURL));
	}

    public DescribeGlobal describeGlobal() {
    	//TODO - convert to command
		try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
					.url(uriBase()+"/sobjects/")
					.method("GET")
					.header("Accept", "application/json")).getStream()){
			return jsonMapper.readValue(is,DescribeGlobal.class);
		} catch (UnsupportedEncodingException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

    public <T> DiscoverSObject<T> discoverSObject(String sobject, Class<T> clazz) {
    	//TODO - convert to command
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
        } catch (IOException e) {
            throw new SFApiException(e);
        }
    }

	public DescribeSObject describeSObject(String sobject) {
		//TODO - convert to command
		try (InputStream is=apiRequest(new HttpRequest(ResponseFormat.STREAM)
					.url(uriBase()+"/sobjects/"+sobject+"/describe")
					.method("GET")
					.header("Accept", "application/json")).getStream()) {
			return jsonMapper.readValue(is,DescribeSObject.class);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
	
	public final String uriBase() {
		return(getSession().getApiEndpoint()+"/services/data/"+config.getApiVersion());
	}
	
	public final <T> T execute(IForceApiCommand<T> command) throws SFApiException {
		return executeGetResponse(command).getResult();
	}
	
	public final <T> ApiCommandResult<T> executeGetResponse(IForceApiCommand<T> command) throws SFApiException {
		HttpRequest req = command.toHttpRequest(uriBase(), getSession().getApiEndpoint());
		HttpResponse response = autoRenew ? apiRequestWithAutoRenew(req) : apiRequest(req);
		return new ApiCommandResult<T>(response, command.interpretResult(response));
	}

	private final HttpResponse apiRequest(HttpRequest req) {
		req.setAuthorization("OAuth "+getSession().getAccessToken());
		req.gzip(gzip);
		HttpResponse res = Http.send(req, config.getHttpsProxy());
		
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
	
	private final HttpResponse apiRequestWithAutoRenew(HttpRequest req) {
		try {
			return apiRequest(req);
		} catch (AuthenticationFailedException ex) {
			// Perform one attempt to auto renew session if possible
			log.debug("Session expired. Refreshing session...");

			if(autoRenew) {
				autoRenew();
			} else {
				if (this.observer != null)
					this.observer.tokenNotRenewedSuccessfully();
				
				throw new AuthenticationFailedException(401,"No refresh token, and 401 found");
			}
			
			try {
				return apiRequest(req);
			} catch (AuthenticationFailedException ex2) {
				throw new RefreshFailedApiException(401,"Tried to refresh but failed.");
			}
		}
	}

	private void autoRenew() {		
		if (this.observer !=null)
			this.observer.tokenNeedsRenewal(getSession().getAccessToken(), getSession().getRefreshToken());
		
		if(getSession().getRefreshToken()!=null) {
			try {
				setSession(Auth.refreshOauthTokenFlow(config, getSession().getRefreshToken()));
				if (this.observer !=null)
					this.observer.tokenRenewedSuccessfully(session);
			} catch (RuntimeException e) {
				if (this.observer !=null)
					this.observer.tokenNotRenewedSuccessfully();
				throw e;
			}
		} else {
			setSession(Auth.authenticate(config));
		}
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
	
	
	/**
	 * @deprecated Please use execute(GetSObjectCommand) instead.
	 */
	@Deprecated public ResourceRepresentation getSObject(String type, String id) throws SFApiException {
		return execute(new GetSObjectCommand(type, id));
	}
	/**
	 * @deprecated Please use execute(CreateSObjectCommand) instead.
	 */
	@Deprecated public String createSObject(String type, Object sObject) { 
		return execute(new CreateSObjectCommand(type, sObject));
	}
	/**
	 * @deprecated Please use execute(CreateSObjectCommand) instead.
	 */
	@Deprecated public String createSObject(String type, Object sObject, ObjectMapper mapper) {
		return execute(new CreateSObjectCommand(type, sObject, mapper));
	}
	/**
	 * @deprecated Please use execute() instead.
	 */
	@Deprecated public void updateSObject(String type, String id, Object sObject) {
		execute(new UpdateSObjectCommand(type, id, sObject));
	}
	/**
	 * @deprecated Please use execute(UpdateSObjectCommand) instead.
	 */
	@Deprecated public void updateSObject(String type, String id, Object sObject, ObjectMapper mapper) {
		execute(new UpdateSObjectCommand(type, id, sObject, mapper));
	}
	/**
	 * @deprecated Please use execute(DeleteSObjectCommand) instead.
	 */
	@Deprecated public void deleteSObject(String type, String id) {
		execute(new DeleteSObjectCommand(type, id));
	}
	/**
	 * @deprecated Please use execute(CreateOrUpdateSObjectCommand) instead.
	 */
	@Deprecated public CreateOrUpdateResult createOrUpdateSObject(String type, String externalIdField, String externalIdValue, Object sObject) {	
		return this.execute(new CreateOrUpdateSObjectCommand(type, externalIdField, externalIdValue, sObject));
	}
	/**
	 * @deprecated Please use execute(QueryCommand)  instead.
	 */
	@Deprecated public <T> QueryResult<T> query(String query, Class<T> clazz) {
        return execute(new QueryCommand<T>(query, clazz));
    }
	/**
	 * @deprecated Please use execute(MapQueryCommand) instead.
	 */
	@SuppressWarnings("rawtypes")
	@Deprecated public QueryResult<Map> query(String query) {
		return execute(new MapQueryCommand(query));
	}
	/**
	 * @deprecated Please use execute(QueryMoreCommand) instead.
	 */
	@Deprecated public <T> QueryResult<T> queryMore(String nextRecordsUrl, Class<T> clazz) {
    	return execute(new QueryMoreCommand<T>(nextRecordsUrl, clazz));
    }
	/**
	 * @deprecated Please use execute(MapQueryMoreCommand) instead.
	 */
    @SuppressWarnings("rawtypes")
    @Deprecated public QueryResult<Map> queryMore(String nextRecordsUrl) {
    	return execute(new MapQueryMoreCommand(nextRecordsUrl));
    }
}
