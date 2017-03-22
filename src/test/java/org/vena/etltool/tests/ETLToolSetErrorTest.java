package org.vena.etltool.tests;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import java.io.UnsupportedEncodingException;

import org.junit.Test;
import org.vena.etltool.ETLClient;
import org.vena.etltool.Main;
import org.vena.etltool.entities.Id;

public class ETLToolSetErrorTest extends ETLToolTest {

	@Test
	public void testSetError() throws UnsupportedEncodingException {
		ETLClient etlClient = mockETLClient();
		Id jobId = uniqueId();
		String[] args = buildCommand(new String[] {"--setError", "Error message", "--jobId", jobId.toString()});
		
		try {
			Main.parseCmdlineArgs(args, etlClient);
		} catch (ExitException e) {
			verify(etlClient).setJobError(jobId.toString(), "Error message");
			assertEquals(0, e.status);
		}
	}
	
	@Test
	public void testSetErrorMissingJobId() throws UnsupportedEncodingException {
		ETLClient etlClient = mockETLClient();
		String[] args = buildCommand(new String[] {"--cancel"});
		try {
			Main.parseCmdlineArgs(args, etlClient);
		} catch (ExitException e) {
			assertEquals(1, e.status);
			assertEquals("Error: You must specify --jobId=<job Id>.\n", err.toString());
		}
	}
}