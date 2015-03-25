package com.force.api;

import java.net.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.security.InvalidParameterException;

public class ApiConfig {

	private String apiVersion = ApiVersion.DEFAULT_VERSION.toString();
	private String loginEndpoint = "https://login.salesforce.com";
	private Proxy httpsProxy;
	private String username;
	private String password;
	String clientId;
	private String clientSecret;
	private String redirectURI;

	public ApiConfig clone() {
		return new ApiConfig()
			.setApiVersion(apiVersion)
			.setHttpsProxy(httpsProxy)
			.setLoginEndpoint(loginEndpoint)
			.setUsername(username)
			.setPassword(password)
			.setClientId(clientId)
			.setClientSecret(clientSecret)
			.setRedirectURI(redirectURI);
	}
	public ApiConfig() { }
	public ApiConfig(String apiVersion) {
		setApiVersion(apiVersion);
	}
	public ApiConfig setForceURL(String url) {
		try {
			URI uri = new URI(url);
			loginEndpoint = "https://"+uri.getHost()+(uri.getPort()>0 ? ":"+uri.getPort() : "");
			String[] params = uri.getQuery().split("&");
			for(String param : params) {
				String[] kv = param.split("=");
				if(kv[0].equals("user")) {
					username = URLDecoder.decode(kv[1],"UTF-8");
				} else if(kv[0].equals("password")) {
					password = URLDecoder.decode(kv[1],"UTF-8");
				} else if(kv[0].equals("oauth_key")) {
					clientId = URLDecoder.decode(kv[1],"UTF-8");
				} else if(kv[0].equals("oauth_secret")) {
					clientSecret = URLDecoder.decode(kv[1],"UTF-8");
				}
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Couldn't parse URL: "+url,e);
		}
		return this;
	}
	
	public ApiConfig setRedirectURI(String redirectURI) {
		this.redirectURI = redirectURI;
		return this;
	}
	
	public ApiConfig setApiVersion(String value) {
		if (!value.matches("^v\\d+.\\d+$"))
			throw new InvalidParameterException("Invalid API version");
		apiVersion = value;
		return this;
	}
	
	public ApiConfig setHttpsProxy(Proxy httpsProxy) {
		this.httpsProxy = httpsProxy;
		return this;
	}
	
	public ApiConfig setUsername(String value) {
		username = value;
		return this;
	}

	public ApiConfig setPassword(String value) {
		password = value;
		return this;
	}

	public ApiConfig setLoginEndpoint(String value) {
		loginEndpoint = value;
		return this;
	}
	
	public ApiConfig setClientId(String value) {
		clientId = value;
		return this;
	}
	
	public ApiConfig setClientSecret(String value) {
		clientSecret = value;
		return this;
	}

	public String getApiVersion() {
		return apiVersion;
	}
	
	public Proxy getHttpsProxy() {
		return httpsProxy;
	}
	
	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getLoginEndpoint() {
		return loginEndpoint;
	}

	public String getClientId() {
		return clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}
	
	public String getRedirectURI() {
		return redirectURI;
	}

}
