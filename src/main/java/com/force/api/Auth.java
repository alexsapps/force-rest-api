package com.force.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.force.api.exceptions.AuthenticationFailedException;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.Http;
import com.force.api.http.HttpRequest;
import com.force.api.http.HttpResponse;

public class Auth {

	private static final ObjectMapper jsonMapper = new ObjectMapper();

	static public final ApiSession oauthLoginPasswordFlow(ApiConfig config) {
		if(config.getClientId()==null) throw new IllegalStateException("clientId cannot be null");
		if(config.getClientSecret()==null) throw new IllegalStateException("clientSecret cannot be null");
		if(config.getUsername()==null) throw new IllegalStateException("username cannot be null");
		if(config.getPassword()==null) throw new IllegalStateException("password cannot be null");
		HttpResponse response = Http.send(HttpRequest.formPost()
				.url(config.getLoginEndpoint()+"/services/oauth2/token")
				.param("grant_type","password")
				.param("client_id",config.getClientId())
				.param("client_secret", config.getClientSecret())
				.param("username",config.getUsername())
				.param("password",config.getPassword()),
				config.getHttpsProxy()
			);
		
		if (response.getResponseCode() == 400) {
			throw new AuthenticationFailedException(400, response.getString());
		}
			
		try (InputStream is=response.getStream()){
			@SuppressWarnings("unchecked")
			Map<String,Object> resp = jsonMapper.readValue(is,Map.class);
			return new ApiSession((String)resp.get("access_token"),(String)resp.get("instance_url"));
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

	
	static public final ApiSession soaploginPasswordFlow(ApiConfig config) {
		if(config.getUsername()==null) throw new IllegalStateException("username cannot be null");
		if(config.getPassword()==null) throw new IllegalStateException("password cannot be null");
		try {
			URL url = new URL(config.getLoginEndpoint()+"/services/Soap/u/23.0");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.addRequestProperty("Content-Type", "text/xml");
			conn.addRequestProperty("SOAPAction", "login");
			OutputStream out = conn.getOutputStream();
			byte[] msg = new String(
					"<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"+
					"<env:Envelope xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\n"+
					"              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"+
					"              xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"+
					"    <env:Body>\n"+
			        "        <n1:login xmlns:n1=\"urn:partner.soap.sforce.com\">\n"+
			        "            <n1:username>"+config.getUsername()+"</n1:username>\n"+
			        "            <n1:password>"+config.getPassword()+"</n1:password>\n"+
			        "        </n1:login>\n"+
			        "    </env:Body>\n"+
			        "</env:Envelope>\n").getBytes("UTF-8");
			out.write(msg);
			out.flush();
			InputStream in = conn.getInputStream();
			StringBuilder b = new StringBuilder();
			byte[] buf = new byte[2000];
			int n = 0;
			while((n=in.read(buf))!=-1) {
				b.append(new String(buf,0,n));
			}
			String s = b.toString();
			//System.out.println(s);
			String accessToken = s.replaceAll("^.*<sessionId>(.*)</sessionId>.*$","$1").trim();
			String apiEndpoint = "https://"+s.replaceAll("^.*<serverUrl>.*https://([^/]*)/.*</serverUrl>.*$","$1").trim();
			//String organizationId = s.replaceAll("^.*<organizationId>(.*)</organizationId>.*$","$1").trim();
			//String userId = s.replaceAll("^.*<userId>(.*)</userId>.*$","$1").trim();
			//System.out.println("accessToken:"+accessToken);
			//System.out.println("apiEndpoint: "+apiEndpoint);
			//System.out.println("userId: "+userId);
			//System.out.println("organizationId: "+organizationId);
							
			return new ApiSession(accessToken, apiEndpoint);
			
			} catch (MalformedURLException e) {
				throw new SFApiException(e);
			} catch (UnsupportedEncodingException e) {
				throw new SFApiException(e);
			} catch (IOException e) {
				throw new SFApiException(e);
			}
	}

	static public final String startOAuthWebServerFlow(AuthorizationRequest req) {
		if(req.apiConfig.getClientId()==null) throw new IllegalStateException("clientId cannot be null");
		if(req.apiConfig.getRedirectURI()==null) throw new IllegalStateException("redirectURI cannot be null");
		try {
			return req.apiConfig.getLoginEndpoint()+
					"/services/oauth2/authorize"+
					"?response_type=code"+
					"&client_id="+URLEncoder.encode(req.apiConfig.getClientId(),"UTF-8")+
					(req.scope!=null ? "&scope="+URLEncoder.encode(req.scope.toString(),"UTF-8") : "") +
					"&redirect_uri="+URLEncoder.encode(req.apiConfig.getRedirectURI(),"UTF-8")+
					(req.state!=null ? "&state="+URLEncoder.encode(req.state,"UTF-8") : "") +
					(req.immediate ? "&immediate=true" : "") +
					(req.display!=null ? "&display="+req.display : "");
		} catch (UnsupportedEncodingException e) {
			throw new SFApiException(e);
		}
	}
	
	static public final ApiSession completeOAuthWebServerFlow(AuthorizationResponse res) {
		if(res.apiConfig.getClientId()==null) throw new IllegalStateException("clientId cannot be null");
		if(res.apiConfig.getClientSecret()==null) throw new IllegalStateException("clientSecret cannot be null");
		if(res.apiConfig.getRedirectURI()==null) throw new IllegalStateException("redirectURI cannot be null");
		if(res.code==null) throw new IllegalStateException("code cannot be null");
		// TODO: throw a (runtime) exception with detailed info if auth failed
		try (InputStream is=Http.send(HttpRequest.formPost()
						.url(res.apiConfig.getLoginEndpoint()+"/services/oauth2/token")
						.header("Accept","application/json")
						.param("grant_type","authorization_code")
						.param("client_id",res.apiConfig.getClientId())
						.param("client_secret", res.apiConfig.getClientSecret())
						.param("redirect_uri",res.apiConfig.getRedirectURI())
						.preEncodedParam("code",res.code),
						res.apiConfig.getHttpsProxy()
					).getStream()){
			Map<?,?> resp = jsonMapper.readValue(
					is,Map.class);

			return new ApiSession()
					.setRefreshToken((String)resp.get("refresh_token"))
					.setAccessToken((String)resp.get("access_token"))
					.setApiEndpoint((String)resp.get("instance_url"));
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}

	static public final ApiSession refreshOauthTokenFlow(ApiConfig config, String refreshToken) {
		if(config.getClientId()==null) throw new IllegalStateException("clientId cannot be null");
		if(config.getClientSecret()==null) throw new IllegalStateException("clientSecret cannot be null");
		// TODO: throw a (runtime) exception with detailed info if auth failed
		try (InputStream is=Http.send(HttpRequest.formPost()
						.url(config.getLoginEndpoint()+"/services/oauth2/token")
						.header("Accept","application/json")
						.param("grant_type","refresh_token")
						.param("client_id",config.getClientId())
						.param("client_secret", config.getClientSecret())
						.param("refresh_token", refreshToken),
						config.getHttpsProxy()
					).getStream()){
			Map<?,?> resp = jsonMapper.readValue(is,Map.class);

			return new ApiSession()
					.setAccessToken((String)resp.get("access_token"))
					.setApiEndpoint((String)resp.get("instance_url"))
					.setRefreshToken(refreshToken);
		} catch (IOException e) {
			throw new SFApiException(e);
		}
	}
	
	/**
	 * revokes a token. Works with both access token and refresh token
	 * @param config
	 * @param token either an access token or a refresh token
	 */
	static public void revokeToken(ApiConfig config, String token) {
		
		try (InputStream is=Http.send(HttpRequest.formPost()
				.header("Accept","*/*")
				.url(config.getLoginEndpoint()+"/services/oauth2/revoke")
				.param("token", token),
				config.getHttpsProxy()).getStream()) {
		// the above does what it needs to
		} catch(Throwable t) {
			// Looks like revoke endpoint closes stream when trying to revoke
			// an already revoked token. It doesn't return an error code. So
			// we'll have to just catch it here and fake the code.
			throw new AuthenticationFailedException(404, "Token could not be revoked. Most likely it has already expired or been revoked.");
		}
	}

	static public final ApiSession authenticate(ApiConfig c) {
		if(c.getUsername()!=null && c.getPassword()!=null && c.getClientId()!=null && c.getClientSecret()!=null) {
			// username/password oauth flow
			return oauthLoginPasswordFlow(c);
		}
		else if(c.getUsername()!=null && c.getPassword()!=null) {
			return soaploginPasswordFlow(c);
		}
		return null;
	}

}
