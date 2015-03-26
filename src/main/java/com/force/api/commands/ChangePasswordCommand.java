package com.force.api.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.force.api.ForceApi;
import com.force.api.exceptions.SFApiException;
import com.force.api.http.HttpRequest;

/**
 * @author a.taylor
 */
public class ChangePasswordCommand extends ForceApiCommand<Object> {
    	
	public ChangePasswordCommand(String userId, String newPassword) {
		
		 // For more information:
        // https://www.salesforce.com/us/developer/docs/api_rest/Content/dome_sobject_user_password.htm
       
        responseFormat(HttpRequest.ResponseFormat.STREAM);
        relativeUri("/sobjects/user/"+userId+"/password");
        method("POST");
        header("Accept", "application/json");
        header("Content-Type", "application/json");
        expectsCode(204);
        try {
			content(ForceApi.getMapper().writeValueAsBytes(new Object() {
			           @JsonProperty
			           public String NewPassword = newPassword; /* capital N in NewPassword is required */
			       }));
		} catch (JsonProcessingException e) {
			throw new SFApiException("Error generating new password JSON.", e);
		}
        
        // note:  changing password does not invalidate session.  tested on 2015-03-25.
	}
    
}
