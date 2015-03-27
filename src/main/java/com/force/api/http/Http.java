package com.force.api.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.force.api.ForceApi;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest.ResponseFormat;


public class Http {
	private final static Logger log = LoggerFactory.getLogger(Http.class);
	private static final int CONN_TIMEOUT = 40000;
	private static final int READ_TIMEOUT = 40000;
		
	static final byte[] readResponse(InputStream stream) throws IOException {
		BufferedInputStream bin = new BufferedInputStream(stream);
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		byte[] buf = new byte[10000];
		int read = 0;
		while((read=bin.read(buf))!=-1) {
			bout.write(buf,0,read);
		}
		return bout.toByteArray();
	}
	
	public static final HttpResponse send(HttpRequest req, Proxy proxy) {
		OutputStream os=null;
		InputStream is=null;
		try {
			URL url = new URL(req.getUrl());
			HttpURLConnection conn = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
			conn.setInstanceFollowRedirects(true);
			conn.setRequestMethod(req.getMethod());
			conn.setConnectTimeout(CONN_TIMEOUT);
			conn.setReadTimeout(READ_TIMEOUT);
			for (HttpRequest.Header h : req.getHeaders()) {
				conn.addRequestProperty(h.getKey(), h.getValue());
			}
			
			if ((req.isGzip()) && req.getResponseFormat().equals(ResponseFormat.STREAM)) {
//				System.out.println("add");
				conn.addRequestProperty("Accept-Encoding", "gzip");
			}
			if(req.getAuthorization()!=null) {
				conn.addRequestProperty("Authorization", req.getAuthorization());
			}
			if (req.getContentBytes() != null) {				
				conn.setDoOutput(true);				
				os = new BufferedOutputStream(conn.getOutputStream());
				os.write(req.getContentBytes());
				os.flush();
			} else if (req.getContentStream() != null) {
				conn.setDoOutput(true);
				final byte[] buf = new byte[2000];
				os = new BufferedOutputStream(conn.getOutputStream());
				int n;
				while ((n = req.getContentStream().read(buf)) >= 0) {
					os.write(buf, 0, n);
				}
				os.flush();
			}			
			int code = conn.getResponseCode();
			String encoding=conn.getContentEncoding();
			boolean gzipResponse=encoding != null && encoding.equalsIgnoreCase("gzip");
			
			if (ForceApi.isDebugMode()) {
				log.info(gzipResponse+" "+encoding);
				log.info(conn.toString());
				log.info(req.toString());
			}
			
			if (code < 300 && code >= 200) {
				
				is = gzipResponse ? new GZIPInputStream(conn.getInputStream()): conn.getInputStream();
				
				switch (req.getResponseFormat()) {
				
				case BYTE: {					
					return new HttpResponse().setByte(readResponse(is))
							.setResponseCode(code);
				}
				case STRING:
					return new HttpResponse().setString(
							new String(readResponse(is), "UTF-8")).setResponseCode(
							code);
				default: {
						HttpResponse r= new HttpResponse().setStream(is).setResponseCode(code);
						is=null; 
						return r;
				}
					
				}
			} else {
				is = gzipResponse ? new GZIPInputStream(conn.getErrorStream()): conn.getErrorStream();
				HttpResponse r = new HttpResponse().setString(
						new String(readResponse(is), "UTF-8")).setResponseCode(code);
				if (ForceApi.isDebugMode())
					log.error("Bad response code: {} on request:\n{}\nmessage:{}", code, req, r.getString());
				else 
					log.error("Bad response code: {}\nmessage:{}", code, r.getString()); // the request may contain passwords, which should not be written to anyone's log files.
				return r;
			}
		} catch (MalformedURLException e) {
			throw new SFApiException(e);
		} catch (IOException e) {
			throw new SFApiException(e);
		} finally {
			close(os);
			close(is);			
		}

	}

	private static void close(InputStream is) {
		try { 
			if (is!=null) is.close();
		} catch (IOException io) {
			//
		}
		
	}

	private static void close(OutputStream os) {
		try {
			if (os !=null) os.close();
		} catch (IOException e) {
			//
		}
		
	}


}
