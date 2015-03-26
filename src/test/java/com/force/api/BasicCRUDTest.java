package com.force.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.force.api.commands.CreateOrUpdateSObjectCommand;
import com.force.api.commands.CreateSObjectCommand;
import com.force.api.commands.DeleteSObjectCommand;
import com.force.api.commands.GetSObjectCommand;
import com.force.api.commands.QueryCommand;
import com.force.api.commands.UpdateSObjectCommand;

/**
 * Smoke test covering the basics.
 * @author jjoergensen
 *
 */
public class BasicCRUDTest {

	static final String TEST_NAME = "force-rest-api basic crud test";

	@Test
	public void basicCRUDTest() {

		ForceApi api = new ForceApi(new ApiConfig()
			.setLoginEndpoint(Fixture.get("loginEndpoint"))
			.setApiVersion(Fixture.get("apiVersion"))
			.setUsername(Fixture.get("username"))
			.setPassword(Fixture.get("password"))
			.setClientId(Fixture.get("clientId"))
			.setClientSecret(Fixture.get("clientSecret")));

		if(api.execute(new QueryCommand<Account>("SELECT name FROM Account WHERE name LIKE '"+TEST_NAME+"%'",Account.class)).getTotalSize()>0) {
			fail("Looks like org is not clean. Manually delete account record with name '"+TEST_NAME+"' before running this test");
		}
		
		try {
			
			// TEST CREATE
			
			Account a = new Account();
			a.setName(TEST_NAME);
			a.setExternalId("1234");
			String id = api.execute(new CreateSObjectCommand("account", a));
			assertNotNull(id);
			
			// TEST GET
			
			a = api.execute(new GetSObjectCommand("account", id)).as(Account.class);
			assertEquals(TEST_NAME,a.getName());
			assertEquals("1234", a.getExternalId());
			
			// TEST UPDATE
			
			a = new Account();
			a.setName(TEST_NAME+", updated");
			api.execute(new UpdateSObjectCommand("account", id, a));
			
			a = api.execute(new GetSObjectCommand("account", id)).as(Account.class);
			assertEquals(TEST_NAME+", updated",a.getName());
			
			// TEST UPSERT: UPDATE
			
			a = new Account();
			a.setName(TEST_NAME);
			a.setAnnualRevenue(3141592.65);

			// test that we get expected result
			assertEquals(CreateOrUpdateResult.UPDATED, api.execute(new CreateOrUpdateSObjectCommand("account", "externalId__c", "1234", a)));
			
			// check that the record was indeed updated
			assertTrue(3141592.65==api.execute(new GetSObjectCommand("account", id)).as(Account.class).getAnnualRevenue());
			
			// check that there's still only one record with this name
			assertTrue(1==api.execute(new QueryCommand<Account>("SELECT id FROM Account WHERE name = '"+TEST_NAME+"'",Account.class)).getTotalSize());
			
			// TEST UPSERT: CREATE
			
			a = new Account();
			a.setName(TEST_NAME+" new one");
			a.setAnnualRevenue(1414213.56);
			
			// test that we get expected result
			assertEquals(CreateOrUpdateResult.CREATED, api.execute(new CreateOrUpdateSObjectCommand("account", "externalId__c", "2345", a)));
			
			// check that the record value was properly set
			assertTrue(1414213.56==api.execute(new QueryCommand<Account>("SELECT annualRevenue FROM Account WHERE externalId__c = '2345'",Account.class)).getRecords().get(0).getAnnualRevenue());
			
		}
		finally {
			QueryResult<Account> res = api.execute(new QueryCommand<Account>("SELECT id FROM Account WHERE name LIKE '"+TEST_NAME+"%'", Account.class));
			for(Account a : res.getRecords()) {
				api.execute(new DeleteSObjectCommand("account", a.getId()));
			}
		}	
	}
}
