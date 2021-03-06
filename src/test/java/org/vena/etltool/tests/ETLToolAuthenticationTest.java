package org.vena.etltool.tests;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;

import org.junit.*;
import org.vena.etltool.ETLClient;
import org.vena.etltool.Main;

public class ETLToolAuthenticationTest extends ETLToolTest {
	
	@Test
	public void testUsernameAndPwd() throws UnsupportedEncodingException {
		ETLClient etlClient = mockETLClient();
		try {
			Main.buildETLMetadata(buildCommandArgs(), etlClient);
		} catch (ExitException e) {
			assertEquals(1, e.status);
			assertEquals("Error: You must specify at least one --file option when submitting a job.", err.toString().trim());
			assertEquals("vena", etlClient.password);
			assertEquals("admin@vena.io", etlClient.username);
		}
	}
	
	@Test
	public void testUsernameAndApiKeyError() throws UnsupportedEncodingException {
		ETLClient etlClient = mockETLClient();
		String[] args = buildCommand(new String[] {"--apiUser", "user", "--apiKey", "key"});
		try {
			Main.buildETLMetadata(args, etlClient);
		} catch (ExitException e) {
			assertEquals(1, e.status);
			assertEquals("Error: You must specify either --username/--password or --apiUser/--apiKey to authenticate with the server, but not both.", err.toString().trim());
		}
	}
}
