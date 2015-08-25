package org.vena.etltool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.vena.api.etl.ETLCubeToStageStep;
import org.vena.api.etl.ETLCubeToStageStep.QueryType;
import org.vena.api.etl.ETLDeleteIntersectionsStep;
import org.vena.api.etl.ETLFileImportStep.FileFormat;
import org.vena.api.etl.ETLFileOld;
import org.vena.api.etl.ETLFileToCubeStep;
import org.vena.api.etl.ETLFileToStageStep;
import org.vena.api.etl.ETLMetadata;
import org.vena.api.etl.ETLMetadata.ETLLoadType;
import org.vena.api.etl.ETLSQLTransformStep;
import org.vena.api.etl.ETLStageToCubeStep;
import org.vena.api.etl.ETLStep.DataType;
import org.vena.etltool.entities.ETLJobDTO;
import org.vena.etltool.entities.ModelResponseDTO;
import org.vena.id.Id;

public class Main {
	
	public static int FIRST_FILE_INDEX = 1;
	
	private static final String EXAMPLE_COMMANDLINE = "etl-tool "
			+ "[--host <addr>] [--port <num>] [--ssl|--nossl]"
			+ "\n{ --apiUser=<uid.cid> --apiKey=<key> "
			+ "\n| --user=<email> --password=<password>"
			+ "\n}"
			+ "\n{ --modelName <name> | --modelId <id> "
			+ "\n}"
			+ "\n{ --loadFromStaging [--wait|--waitFully]"
			+ "\n| [--stage|--stageOnly] [--wait|--waitFully] [--validate] [--templateId <id>] [--jobName <name>] --file \"[file=]<filename>; [type=]<filetype> [;[table=]<tableName>] [;format={CSV|TDF}] [;bulkInsert={true|false}]\""
			+ "\n| --cancel --jobId <id>"
			+ "\n| --setError --jobId <id>"
			+ "\n| --status --jobId <id>"
			+ "\n| --transformComplete --jobId <id>"
			+ "\n| --delete <type> --deleteQuery <expr>"
			+ "\n| --export <type>\n {--exportQuery <expr> | --exportWhere <clause>}\n {--exportToFile <name> [--excludeHeaders] | --exportToTable <name> [--background]}"
			+ "\n}";
	
	/**
	 * @param args
	 * @throws UnsupportedEncodingException 
	 */
	public static void main(String[] args) throws UnsupportedEncodingException {
		System.getProperties().setProperty("datacenterId", "1");

		ETLClient etlClient = new ETLClient();

		ETLMetadata metadata = parseCmdlineArgs(args, etlClient);

		System.out.print("Submitting job... ");
		ETLJobDTO etlJob = etlClient.uploadETL(metadata);
		System.out.println("OK");
		System.out.println("Job submitted. Your ETL Job Id is "+etlJob.getId());
		System.out.println("Job status is " + etlJob.getStatus());
		
		/* If polling option was provided, poll until the task completes. */
		if( etlClient.pollingRequested  ) {
			System.out.println("Waiting for job to finish... ");
			etlClient.pollTillJobComplete(etlJob.getId(), etlClient.waitFully);
			System.out.println("Done.");
		}
	}


	@SuppressWarnings("static-access")
	private  static ETLMetadata parseCmdlineArgs(String[] args, ETLClient etlClient) throws UnsupportedEncodingException {
		Options options  = new Options();

		Option helpOption =  OptionBuilder
				.withLongOpt( "help")
				.withDescription("Print this message" )
				.isRequired(false)
				.create();

		options.addOption(helpOption);

		Option versionOption =  OptionBuilder
				.withLongOpt( "version")
				.withDescription("Print the version of the cmdline tool." )
				.isRequired(false)
				.create();

		options.addOption(versionOption);

		Option statusOption =  OptionBuilder
				.withLongOpt( "status")
				.withDescription("Request status for the specified etlJob.  Requires --jobId option." )
				.isRequired(false)
				.create();

		options.addOption(statusOption);

		Option sslOption = 
				OptionBuilder
				.withLongOpt("ssl")
				.isRequired(false)
				.withDescription("Use SSL encryption to connect to the server (this is default).")
				.create();

		options.addOption(sslOption);

		Option noSSLOption = 
				OptionBuilder
				.withLongOpt("nossl")
				.isRequired(false)
				.withDescription("Don't use SSL encryption to connect to the server.")
				.create();

		options.addOption(noSSLOption);

		Option apiUserOption = 
				OptionBuilder
				.withLongOpt("apiUser")
				.isRequired(false)
				.hasArg()
				.withArgName("uid.cid")
				.withDescription("The api user to use to access the API. Example 38450575909584901.2, (user 38450575909584901, customer 2). "+
						"Note: This is different from the username used to login!")
						.create();

		options.addOption(apiUserOption);

		Option apiKeyOption = 
				OptionBuilder
				.withLongOpt("apiKey")
				.isRequired(false)
				.hasArg()
				.withArgName("key")
				.withDescription("The api key to use to access the API. Example 4d87c176227045de9628fb5f010a7b40. "+
						"Note: This is different from the password used to login!")
						.create();

		options.addOption(apiKeyOption);

		Option usernameOption = 
				OptionBuilder
				.withLongOpt("username")
				.isRequired(false)
				.hasArg()
				.withArgName("email")
				.withDescription("The username to use to access the API. This is the same username you would use to login to the vena application.")
				.create('u');

		options.addOption(usernameOption);

		Option passwordOption = 
				OptionBuilder
				.withLongOpt("password")
				.isRequired(false)
				.hasArg()
				.withArgName("password")
				.withDescription("The password to use to access the API. This is the same password you would use to login to the vena application.")
				.create('p');

		options.addOption(passwordOption);

		Option hostOption = 
				OptionBuilder
				.withLongOpt("host")
				.isRequired(false)
				.hasArg()
				.withArgName("addr")
				.withDescription("The hostname of the API server to connect to.  Defaults to vena.io.")
				.create();

		options.addOption(hostOption);

		Option portOption = 
				OptionBuilder
				.withLongOpt("port")
				.isRequired(false)
				.hasArg()
				.withArgName("num")
				.withDescription("The port to connect to on the API server.  Defaults to 443 with SSL or 80 without SSL.")
				.create();

		portOption.setType(Integer.class);
		options.addOption(portOption);

		Option modelId = 
				OptionBuilder
				.withLongOpt("modelId")
				.isRequired(false)
				.hasArg()
				.withArgName("id")
				.withDescription("The Id of the model to apply the etl job to. See also --modelName.")
				.create();

		options.addOption(modelId);

		Option modelName = 
				OptionBuilder
				.withLongOpt("modelName")
				.isRequired(false)
				.hasArg()
				.withArgName("name")
				.withDescription("The name of the model to apply the etl job to. See also --modelId.")
				.create();

		options.addOption(modelName);

		Option createModel = 
				OptionBuilder
				.withLongOpt("createModel")
				.isRequired(false)
				.hasArg()
				.withArgName("name")
				.withDescription("Will cause a brand new model to be created with the specified name.  See also: --modelId.")
				.create();

		options.addOption(createModel);

		Option fileOption = 
				OptionBuilder
				.withLongOpt("file")
				.isRequired(false)
				.hasArg()
				.withArgName("options")
				.withDescription("A data file to import (multiple allowed)."
						+ "\n -F \"[file=]<filename>; [type=]<filetype> [;[table=]<tableName>] [;format={CSV|TDF}] [;bulkInsert={true|false}]\""
						+ "\n where <filetype> is one of {"+ETLFileOld.SUPPORTED_FILETYPES_LIST+"}>."
						+ "\n Example: -F model.csv;hierarchy"
						+ "\n Example: -F file=values.tdf;format=TDF;type=intersections")
				.create('F');

		options.addOption(fileOption);

		Option stageOption = 
				OptionBuilder
				.withLongOpt("stage")
				.isRequired(false)
				.withDescription("Load the files into the SQL staging area and await SQL transform.")
				.create();

		options.addOption(stageOption);

		Option stage2Option = 
				OptionBuilder
				.withLongOpt("stageAndTransform")
				.isRequired(false)
				.withDescription("Load the files into the SQL staging area and await SQL transform.")
				.create();

		options.addOption(stage2Option);

		Option stageOnlyOption = 
				OptionBuilder
				.withLongOpt("stageOnly")
				.isRequired(false)
				.withDescription("Load the files into the SQL staging area.")
				.create();

		options.addOption(stageOnlyOption);

		Option transformCompleteOption = 
				OptionBuilder
				.withLongOpt("transformComplete")
				.isRequired(false)
				.withDescription("Signal to the server that SQL transform is complete and to start loading from the SQL staging area. Requires --jobId option.")
				.create();

		options.addOption(transformCompleteOption);

		Option loadFromStagingOption = 
				OptionBuilder
				.withLongOpt("loadFromStaging")
				.isRequired(false)
				.withDescription("Load data from the SQL staging area. This creates a new job ID.")
				.create();

		options.addOption(loadFromStagingOption);

		Option setErrorOption = 
				OptionBuilder
				.withLongOpt("setError")
				.isRequired(false)
				.hasOptionalArg()
				.withArgName("msg")
				.withDescription("Set the job status to error with optional error message. Requires --jobId option.")
				.create();

		options.addOption(setErrorOption);

		Option cancelOption = 
				OptionBuilder
				.withLongOpt("cancel")
				.isRequired(false)
				.withDescription("Request a job to be cancelled. Requires --jobId option.")
				.create();

		options.addOption(cancelOption);

		Option jobIdOption = 
				OptionBuilder
				.withLongOpt("jobId")
				.isRequired(false)
				.hasArg()
				.withArgName("id")
				.withDescription("Specify a job ID (for certain operations). Example: --jobId=79026536904130560")
				.create();

		options.addOption(jobIdOption);

		Option jobNameOption = 
				OptionBuilder
				.withLongOpt("jobName")
				.isRequired(false)
				.hasArg()
				.withArgName("name")
				.withDescription("Specify a job name (when creating a new job only)")
				.create();

		options.addOption(jobNameOption);

		Option templateOption = 
				OptionBuilder
				.withLongOpt("templateId")
				.isRequired(false)
				.hasArg()
				.withArgName("id")
				.withDescription("Specify a template ID to associate when creating a new job")
				.create();

		options.addOption(templateOption);

		Option validateOption = 
				OptionBuilder
				.withLongOpt("validate")
				.isRequired(false)
				.withDescription("Validate the import files.  Performs a dry run without saving data, and sends back a list of validation results.")
				.create();

		options.addOption(validateOption);

		Option exportOption = 
				OptionBuilder
				.withLongOpt("export")
				.isRequired(false)
				.hasArg()
				.withArgName("type")
				.withDescription("Export part of the datamodel to a staging table. <type> may be one of {"+ETLFileOld.SUPPORTED_FILETYPES_LIST+"}.")
				.create();

		options.addOption(exportOption);

		Option exportStagingOption = 
				OptionBuilder
				.withLongOpt("exportToTable")
				.isRequired(false)
				.hasArg()
				.withArgName("name")
				.withDescription("Name of table in staging DB to export to.")
				.create();

		options.addOption(exportStagingOption);

		Option exportFileOption = 
				OptionBuilder
				.withLongOpt("exportToFile")
				.isRequired(false)
				.hasArg()
				.withArgName("name")
				.withDescription("Name of file to export to.")
				.create();

		options.addOption(exportFileOption);

		Option exportWhereOption = 
				OptionBuilder
				.withLongOpt("exportWhere")
				.isRequired(false)
				.hasArg()
				.withArgName("clause")
				.withDescription("Where clause for export (HQL). May not be combined with --exportQuery.")
				.create();

		options.addOption(exportWhereOption);

		Option exportQueryOption = 
				OptionBuilder
				.withLongOpt("exportQuery")
				.isRequired(false)
				.hasArg()
				.withArgName("expr")
				.withDescription("Query expression for export (model slice language).  May not be combined with --exportWhere.")
				.create();

		options.addOption(exportQueryOption);

		Option excludeHeadersOption = 
				OptionBuilder
				.withLongOpt("excludeHeaders")
				.isRequired(false)
				.withDescription("Exclude header row when exporting to file.")
				.create();

		options.addOption(excludeHeadersOption);

		Option deleteOption = 
				OptionBuilder
				.withLongOpt("delete")
				.isRequired(false)
				.hasArg()
				.withArgName("type")
				.withDescription("Delete all <type> from the datamodel that matches --deleteQuery. <type> can be one of {intersections}.")
				.create();

		options.addOption(deleteOption);

		Option deleteQueryOption = 
				OptionBuilder
				.withLongOpt("deleteQuery")
				.isRequired(false)
				.hasArg()
				.withArgName("expr")
				.withDescription("The query expression to match for --delete.")
				.create();

		options.addOption(deleteQueryOption);

		Option waitOption = 
				OptionBuilder
				.withLongOpt("wait")
				.isRequired(false)
				.withDescription("Wait for job to complete (or fail) before returning. Returns status code 0 if the job was successful and non-zero if it failed. "
						+ "For jobs run with --stage or --stageAndTransform, this will only wait until the job has completed the first step (reached IN_STAGING).")
				.create();

		options.addOption(waitOption);

		Option waitFullyOption = 
				OptionBuilder
				.withLongOpt("waitFully")
				.isRequired(false)
				.withDescription("Wait for job to fully complete (or fail) before returning. Returns status code 0 if the job was successful and non-zero if it failed. "
						+ "For jobs run with --stage or --stageAndTransform, this will wait until the job has fully completed.")
				.create();

		options.addOption(waitFullyOption);

		Option verboseOption = 
				OptionBuilder
				.withLongOpt("verbose")
				.isRequired(false)
				.withDescription("Show the server calls made while the command runs.")
				.create();

		options.addOption(verboseOption);
		
		Option loadStepsOption = 
				OptionBuilder
				.withLongOpt("loadSteps")
				.isRequired(false)
				.hasArg()
				.withArgName("fileName")
				.withDescription("Name of file containing load steps.")
				.create();

		options.addOption(loadStepsOption);

		Option backgroundOption = 
				OptionBuilder
				.withLongOpt("background")
				.isRequired(false)
				.withDescription("Use with --export command to run it in the background. Creates a job Id.")
				.create('b');

		options.addOption(backgroundOption);
		
		HelpFormatter helpFormatter = new HelpFormatter();

		CommandLine commandLine = null;

		CommandLineParser parser = new GnuParser();
		try {
			// parse the command line arguments
			commandLine = parser.parse( options, args );
		}
		catch( ParseException exp ) {
			System.err.println( "Error: " + exp.getMessage() );

			System.exit(1);
		}

		if(commandLine.hasOption("help") || args.length == 0) {

			helpFormatter.printHelp(EXAMPLE_COMMANDLINE, options);

			System.exit(0);
		}

		if(commandLine.hasOption("version")) {

			System.out.print(ETLClient.requestVersionInfo());
			System.exit(0);
		}



		String port = commandLine.getOptionValue("port");

		if( port != null)
			etlClient.port=Integer.parseInt(port);



		String hostname = commandLine.getOptionValue("host");

		if( hostname != null) {
			etlClient.host=hostname;
		}

		if( commandLine.hasOption("ssl") && commandLine.hasOption("nossl") ) { 
			System.err.println( "Error: --ssl and --nossl options cannot be combined.");

			System.exit(1);
		}

		if( commandLine.hasOption("ssl") ) { 
			etlClient.protocol = "https";
		}

		else if( commandLine.hasOption("nossl") ) { 
			etlClient.protocol = "http";
		}

		if( commandLine.hasOption("wait") ) { 
			etlClient.pollingRequested = true;
			etlClient.waitFully = false;
		}

		if( commandLine.hasOption("waitFully") ) { 
			etlClient.pollingRequested = true;
			etlClient.waitFully = true;
		}

		if( commandLine.hasOption("verbose") ) { 
			etlClient.verbose = true;
		}

		String apiUser =  commandLine.getOptionValue("apiUser");

		etlClient.apiUser = apiUser;

		String apiKey =  commandLine.getOptionValue("apiKey");

		etlClient.apiKey = apiKey;

		String username =  commandLine.getOptionValue("username");

		etlClient.username = username;

		String password =  commandLine.getOptionValue("password");

		etlClient.password = password;

		String jobId =  commandLine.getOptionValue("jobId");

		String templateId = commandLine.getOptionValue("templateId");

		etlClient.templateId = templateId;

		/* Cross validation for the authentication options. */
		if( apiKey == null && username == null) {
			System.err.println( "Error: You must specify either --username/--password options  or --apiUser/--apiKey to authenticate with the server.");

			System.exit(1);
		}

		if( (apiKey != null || apiUser != null ) ) {
			if(username !=null || password !=null) {
				System.err.println( "Error: apiKey and username/password options cannot be combined.  Use either --username and --pasword together, or --apiUser and --apiKey together.");

				System.exit(1);
			}
		}

		if( (username != null || password != null ) ) {

			if(apiKey !=null || apiUser !=null) {
				System.err.println( "Error: apiKey and username/password options cannot be combined.  Use either --username and --pasword together, or --apiUser and --apiKey together.");

				System.exit(1);
			}

			System.out.print("Logging in... ");
			etlClient.login();
			System.out.println("OK");
		}

		// Options that work on a single job ID

		if(commandLine.hasOption("status") || args.length == 0) {

			if (jobId == null) {
				System.err.println( "Error: You must specify --jobId=<job Id>.");
				System.exit(1);
			}

			System.out.print("Fetching job status... ");
			ETLJobDTO etlJob = etlClient.requestJobStatus(jobId);
			System.out.println("OK");

			ETLClient.printJobStatus(etlJob);

			System.exit(0);
		}

		if (commandLine.hasOption("transformComplete")) {

			if (jobId == null) {
				System.err.println( "Error: You must specify --jobId=<job Id>.");
				System.exit(1);
			}

			System.out.print("Signalling job to continue... ");
			ETLJobDTO etlJob = etlClient.sendTransformComplete(jobId);
			System.out.println("OK");
			
			/* If polling option was provided, poll until the task completes. */
			if( etlClient.pollingRequested  ) {
				System.out.println("Waiting for job to finish... ");
				etlClient.pollTillJobComplete(etlJob.getId(), etlClient.waitFully);
				System.out.println("Done.");
			}
			
			System.exit(0);
		}

		if (commandLine.hasOption("setError")) {

			if (jobId == null) {
				System.err.println( "Error: You must specify --jobId=<job Id>.");
				System.exit(1);
			}

			System.out.print("Setting job error status... ");
			etlClient.setJobError(jobId, commandLine.getOptionValue("setError"));
			System.out.println("OK");
			System.exit(0);
		}

		if (commandLine.hasOption("cancel")) {

			if (jobId == null) {
				System.err.println( "Error: You must specify --jobId=<job Id>.");
				System.exit(1);
			}

			System.out.print("Cancelling job... ");
			etlClient.sendCancel(jobId);
			System.out.println("OK");
			System.exit(0);
		}
		
		String modelIdStr = commandLine.getOptionValue("modelId");
		String modelNameStr = commandLine.getOptionValue("modelName");

		if (commandLine.hasOption("validate")) {
			etlClient.validationRequested = true;
		}

		/* Process model parameters. Create a new model if necessary. */
		if( modelIdStr != null || modelNameStr != null) {

			if(modelIdStr != null && modelNameStr != null)  {
				System.err.println( "Error: You must specify either --modelId=<existing model Id> or --modelName=<model name>, but not both.");

				System.exit(1);
			}

			//Lookup model by name.
			if( modelNameStr != null ) {
				System.out.print("Looking up model... ");
				ModelResponseDTO searchResults = etlClient.lookupModel(modelNameStr);

				if( searchResults == null) {
					System.err.println( "Error: Could not find the model named \""+modelNameStr+"\".");

					System.exit(1);
				}

				System.out.println("OK");
				etlClient.modelId = searchResults.getId();
			}
			else {
				etlClient.modelId = Id.valueOf(modelIdStr);
			}
		}
		else if(commandLine.getOptionValue("createModel") !=null) {
			System.out.print("Creating new model... ");
			etlClient.createModel(commandLine.getOptionValue("createModel"));

			System.out.println("OK");
		}
		else {
			System.err.println( "Error: You must specify one of --createModel=<model name> or --modelId or --modelName.");

			System.exit(1);
		}
		
		// ETL 2.0 option for providing multiple steps at a time
		
		if (commandLine.hasOption("loadSteps")) {
			if (commandLine.getOptionValue("loadSteps") == null) {
				System.err.println( "Error: loadSteps option requires --loadSteps <fileName>.");
				System.exit(1);
			}
			return produceStepsMetadata(etlClient, commandLine);
		}

		// Options that require a data model

		if (commandLine.hasOption("export")) {
			String exportTypeStr = commandLine.getOptionValue("export");
			String exportToTable = commandLine.getOptionValue("exportToTable");
			String exportToFile = commandLine.getOptionValue("exportToFile");
			boolean background = commandLine.hasOption("background");

			if (exportToFile != null && exportToTable != null)  {
				System.err.println( "Error: --exportToTable and --exportToFile options cannot be combined.");
				System.exit(1);
			}
			
			if (exportToFile == null && exportToTable == null) {
				System.err.println( "Error: export option requires either --exportToTable <name> or --exportToFile <name>.");
				System.exit(1);
			}

			if (exportToFile != null && background) {
				System.err.println( "Error: --exportToFile does not support --background option.");
				System.exit(1);
			}

			DataType type = null;
			try {
				type = DataType.valueOf(exportTypeStr);
			}
			catch(IllegalArgumentException e) {
				System.err.println( "Error: The ETL file type \""+exportTypeStr+"\" does not exist. The known filetypes are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
				System.exit(1);
			}

			String whereClause = commandLine.getOptionValue("exportWhere");
			String queryExpr = commandLine.getOptionValue("exportQuery");
			
			if (whereClause != null && queryExpr != null) {
				System.err.println( "Error: exportWhere and exportQuery options cannot be combined.");
				System.exit(1);
			}

			boolean excludeHeaders = commandLine.hasOption("excludeHeaders");

			if (!background) {
				if (exportToTable != null) {
					System.out.println("WARNING: Running this command in the foreground is not recommended! Use the --background option to avoid potential timeout problems.");
				}
				System.out.print("Running export (this might take a while)... ");
				InputStream in = etlClient.sendExport(type, exportToFile != null, exportToTable, whereClause, queryExpr, !excludeHeaders);
				if (exportToFile != null) {
					try {
						Files.copy(in, new File(exportToFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						e.printStackTrace();
						try {
							in.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						System.exit(1);
					}
				}
				System.out.print("OK.");
				System.exit(0);
				
			} else {
				System.out.println("Creating a new job.");
				
				ETLMetadata metadata = new ETLMetadata();
					
				metadata.setSchemaVersion(2);
				metadata.setModelId(etlClient.modelId);
				
				ETLCubeToStageStep step = new ETLCubeToStageStep();
				
				step.setDataType(type);
				step.setTableName(exportToTable);
				if (whereClause != null) {
					step.setQueryType(QueryType.HQL);
					step.setQueryString(whereClause);
				} else if (queryExpr != null) {
					step.setQueryType(QueryType.MODEL_SLICE);
					step.setQueryString(queryExpr);
				} else if ((type == DataType.intersections) || (type == DataType.lids)) {
					step.setQueryType(QueryType.MODEL_SLICE);
				}
				metadata.addStep(step);

				String jobName =  commandLine.getOptionValue("jobName");
				metadata.setName(jobName);
				
				return metadata;
			}			

		}

		if (commandLine.hasOption("delete")) {
			String deleteTypeStr = commandLine.getOptionValue("delete");
			String expr = commandLine.getOptionValue("deleteQuery");
			boolean background = commandLine.hasOption("background");

			if (expr == null) {
				System.err.println("Error: delete option requires --deleteQuery <expr>.");
				System.exit(1);
			}

			DataType type = null;
			try {
				type = DataType.valueOf(deleteTypeStr);
				if (!type.equals(DataType.intersections)) 
					throw new IllegalArgumentException();
			}
			catch(IllegalArgumentException e) {
				System.err.println( "Error: The ETL file type \""+deleteTypeStr+"\" is not supported. The supported filetype is intersections.");
				System.exit(1);
			}
			
			if (!background) {
				System.out.println("WARNING: Running this command in the foreground is not recommended! Use the --background option to avoid potential timeout problems.");
				System.out.print("Running delete (this might take a while)... ");
				etlClient.sendDelete(type, expr);
				System.out.print("OK.");
				System.exit(0);
			} else {
				System.out.println("Creating a new job.");

				ETLMetadata metadata = new ETLMetadata();

				metadata.setSchemaVersion(2);
				metadata.setModelId(etlClient.modelId);

				ETLDeleteIntersectionsStep step = new ETLDeleteIntersectionsStep();
				step.setDataType(type);
				step.setExpression(expr);

				metadata.addStep(step);

				String jobName = commandLine.getOptionValue("jobName");
				metadata.setName(jobName);

				return metadata;
			}
		}

		// Do an Import
		return produceImportMetadata(commandLine, etlClient.modelId);
	}

	private static ETLMetadata produceStepsMetadata(ETLClient etlClient, CommandLine commandLine) {
		
		System.out.println("Creating a new job.");
		
		ETLMetadata metadata = new ETLMetadata();

		String jobName =  commandLine.getOptionValue("jobName");
		metadata.setName(jobName);
		metadata.setSchemaVersion(2);
		metadata.setModelId(etlClient.modelId);
		
		String stepsFile = commandLine.getOptionValue("loadSteps");
		
		try (BufferedReader br = new BufferedReader(new FileReader(new File(stepsFile).getPath()))) {
				
			    String line;
			    while ((line = br.readLine()) != null) {
			    	System.out.println(line);
			    	String[] optionFields = line.trim().split(" ", 2);
			    	String loadType = optionFields[0];
			    	
					ETLFileOld etlFile = null;
			    	
			    	switch (loadType.toUpperCase()) {
			    	case "FILETOCUBE":
			    	{
			    		etlFile = prepareFilesToLoad(optionFields);
						metadata.addStep(new ETLFileToCubeStep(etlFile));
			    		break;
			    	}
			    	case "FILETOSTAGE":
			    	{
			    		etlFile = prepareFilesToLoad(optionFields);
						metadata.addStep(new ETLFileToStageStep(etlFile));
			    		break;
			    	}
			    	case "SQLTRANSFORM":
			    	{
						metadata.addStep(new ETLSQLTransformStep());
			    		break;
			    	}
			    	case "STAGETOCUBE":
			    	{
			    		if (optionFields.length == 1) {
							metadata.addStep(new ETLStageToCubeStep(DataType.hierarchy));
							metadata.addStep(new ETLStageToCubeStep(DataType.attributes));
							metadata.addStep(new ETLStageToCubeStep(DataType.intersections));
							metadata.addStep(new ETLStageToCubeStep(DataType.lids));
			    		} else if (optionFields.length == 2) {
			    			String[] parts = optionFields[1].split("=", 2);
			    			String key = parts[0].trim();
							String value = parts[1].trim();
			    			if (key.equalsIgnoreCase("type")) {
			    				try {
									metadata.addStep(new ETLStageToCubeStep(DataType.valueOf(DataType.class, value)));
								} catch (IllegalArgumentException e) {
									System.err.println( "Error: The ETL file type \""+value+"\" does not exist. The known filetypes are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
									System.exit(1);
								}
			    			} else {
								System.err.println( "Error: stageToCube valid option is type=<type>. The known types are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
								System.exit(1);
			    			}
			    		} else {
							System.err.println( "Error: stageToCube valid option is type=<type>. The known types are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
							System.exit(1);
			    		}
			    		break;
			    	}
			    	case "CUBETOSTAGE": 
			    	{
						ETLCubeToStageStep step = new ETLCubeToStageStep();
						
						if (optionFields.length != 2)
						{
							System.err.println( "Error: cubeToStage step requires type=<type>;table=<name>. The known types are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
							System.exit(1);
						}
						
						String[] fields = optionFields[1].split(";");
						
						boolean typeFound = false;
						boolean tableFound = false;
						
						for (String field : fields) {
							String[] parts = field.split("=", 2);

							if (parts.length == 2) {
								String key = parts[0].trim();
								String value = parts[1].trim();

								switch (key) {
								case "type":
									try {
										step.setDataType(DataType.valueOf(DataType.class, value));
										typeFound = true;
									} catch (IllegalArgumentException e) {
										throw new IllegalArgumentException("The ETL file type \""+value+"\" does not exist. The supported filetypes are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
									}
									break;
								case "table":
									step.setTableName(value);
									tableFound = true;
									break;
								case "exportQuery":
									step.setQueryType(QueryType.MODEL_SLICE);
									step.setQueryString(value);
									break;
								case "exportWhere":
									step.setQueryType(QueryType.HQL);
									step.setQueryString(value);
									break;
								default:
									throw new IllegalArgumentException("Unsupported key " + key);
								}
							} else {
								throw new IllegalArgumentException("The field "+ field +" contained "+ parts.length +" parts.");
							}
						}
						
						if (!typeFound || !tableFound) {
							System.err.println( "Error: cubeToStage step requires type=<type>;table=<name>. The known types are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
							System.exit(1);
						}
						
						if (step.getQueryType() == null) {
							if ((step.getDataType() == DataType.intersections) || (step.getDataType() == DataType.lids))
								step.setQueryType(QueryType.MODEL_SLICE);
						}

						metadata.addStep(step);
						break;
			    	}
			    	case "":
			    		break;
			    	default:
						System.err.println("Error: loadType " + loadType + " not supported. "
								+ "Supported options are { fileToCube, fileToStage, SQLTransform, stageToCube, cubeToStage }.");
						System.exit(1);	
			    	}
			    }
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		return metadata;
	}


	private static ETLFileOld prepareFilesToLoad(String[] optionFields) {
		
		if (optionFields.length < 2)
		{
			System.err.println( "\nPlease specify the filename followed by the file type, table name, and optional arguments."
					+ "\n Example: --file intersections.csv;intersections"
					+ "\n Example: --file arbitrary.csv;user_defined;mytable;bulkInsert=true");
			System.err.println( "\nOr specify options in any order using key-value pairs."
					+ "\n Example: --file \"file=arbitrary.csv; type=user_defined; table=mytable; format=CSV; bulkInsert=true\"");
			System.exit(1);
		}
		
		ETLFileOld etlFile = null;
		try {
			etlFile = parseETLFileArgs(optionFields[1].replaceAll("\"", ""));
			String key = "file" + (FIRST_FILE_INDEX++);			
			etlFile.setMimePart(key);
		}
		catch (IllegalArgumentException e) {
			System.err.println( "Error: The value \"" + optionFields[1] + "\" is invalid for a file loading step.  " + e.getMessage());
			System.err.println( "\nPlease specify the filename followed by the file type, table name, and optional arguments."
					+ "\n Example: --file intersections.csv;intersections"
					+ "\n Example: --file arbitrary.csv;user_defined;mytable;bulkInsert=true");
			System.err.println( "\nOr specify options in any order using key-value pairs."
					+ "\n Example: --file \"file=arbitrary.csv; type=user_defined; table=mytable; format=CSV; bulkInsert=true\"");
			System.exit(1);
		}
		
		return etlFile;
	}

	private static ETLMetadata produceImportMetadata(CommandLine commandLine, Id modelId) {

		ETLLoadType loadType = ETLLoadType.FILE_TO_CUBE;

		int numStageOptions = 0;
		
		if (commandLine.hasOption("stage") || commandLine.hasOption("stageAndTransform")) {
			loadType = ETLLoadType.FILE_TO_STAGE_TO_CUBE;
			numStageOptions++;
		}
		if (commandLine.hasOption("stageOnly")) {
			loadType = ETLLoadType.FILE_TO_STAGE;
			numStageOptions++;
		}
		if (commandLine.hasOption("loadFromStaging")) {
			loadType = ETLLoadType.STAGE_TO_CUBE;
			numStageOptions++;
		}

		if (numStageOptions > 1) {
			System.err.println( "Error: --stage, --stageAndTransform, --stageOnly, and --loadFromStaging options cannot be combined. At most one of these options can be used at a time.");
			System.exit(1);
		}

		String[] etlFileOptionValues = commandLine.getOptionValues("file");

		if (etlFileOptionValues == null && loadType != ETLLoadType.STAGE_TO_CUBE) {
			System.err.println( "Error: You must specify at least one --file option when submitting a job.");

			System.exit(1);
		}
		
		if (etlFileOptionValues != null && loadType == ETLLoadType.STAGE_TO_CUBE) {
			System.err.println( "Error: --file and --loadFromStaging options cannot be combined.");

			System.exit(1);
		}

		List<ETLFileOld> etlFiles = new ArrayList<>();

		if (etlFileOptionValues != null) {
			
			for(String etlFileOption : etlFileOptionValues)  {
				try {
					ETLFileOld etlFile = parseETLFileArgs(etlFileOption);
					etlFiles.add(etlFile);
				}
				catch (IllegalArgumentException e) {
					System.err.println( "Error: The value \""+etlFileOption+"\" for option --file is invalid.  " + e.getMessage());
					System.err.println( "\nPlease specify the filename followed by the file type, table name, and optional arguments."
							+ "\n Example: --file intersections.csv;intersections"
							+ "\n Example: --file arbitrary.csv;user_defined;mytable;bulkInsert=true");
					System.err.println( "\nOr specify options in any order using key-value pairs."
							+ "\n Example: --file \"file=arbitrary.csv; type=user_defined; table=mytable; format=CSV; bulkInsert=true\"");
					System.exit(1);
				}
			}
			
			for(ETLFileOld etlFile : etlFiles) {
				String key = "file" + (FIRST_FILE_INDEX++);			
				etlFile.setMimePart(key);
			}
		}
		
		System.out.println("Creating a new job.");

		ETLMetadata metadata = new ETLMetadata();

		switch(loadType) {
		case FILE_TO_CUBE:
			for(ETLFileOld file : etlFiles) {
				metadata.addStep(new ETLFileToCubeStep(file));
			}
			break;
		case FILE_TO_STAGE:
			for(ETLFileOld file : etlFiles) {
				metadata.addStep(new ETLFileToStageStep(file));
			}
			break;
		case FILE_TO_STAGE_TO_CUBE:
			for(ETLFileOld file : etlFiles) {
				metadata.addStep(new ETLFileToStageStep(file));
			}
			metadata.addStep(new ETLSQLTransformStep(etlFiles, null));
			// fall-through:
		case STAGE_TO_CUBE:
			metadata.addStep(new ETLStageToCubeStep(DataType.hierarchy));
			metadata.addStep(new ETLStageToCubeStep(DataType.attributes));
			metadata.addStep(new ETLStageToCubeStep(DataType.intersections));
			metadata.addStep(new ETLStageToCubeStep(DataType.lids));
			break;
		}
		
		metadata.setSchemaVersion(2);
		metadata.setLoadType(loadType);
		metadata.setModelId(modelId);

		String jobName =  commandLine.getOptionValue("jobName");
		metadata.setName(jobName);

		return metadata;
		
	}

	private static ETLFileOld parseETLFileArgs(String etlFileOption) {
		ETLFileOld etlFile = new ETLFileOld();

		String[] optionFields = etlFileOption.split(";");

		List<String> unqualifiedFields = new ArrayList<>();

		for (String field : optionFields) {
			String[] parts = field.split("=", 2);

			if (parts.length == 1) {
				unqualifiedFields.add(field.trim());
			}
			else if (parts.length == 2) {
				String key = parts[0].trim();
				String value = parts[1].trim();

				switch (key) {
				case "bulkInsert":
					etlFile.setBulkInsert(Boolean.valueOf(value));
					break;
				case "file":
					etlFile.setFilename(value);
					break;
				case "format":
					etlFile.setFileFormat(FileFormat.valueOf(FileFormat.class, value));
					break;
				case "table":
					etlFile.setTableName(value);
					break;
				case "type":
					try {
						etlFile.setFileType(DataType.valueOf(DataType.class, value));
					} catch (IllegalArgumentException e) {
						throw new IllegalArgumentException("The ETL file type \""+value+"\" does not exist. The supported filetypes are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
					}
					break;
				default:
					throw new IllegalArgumentException("Unsupported key " + key);
				}
			}
			else {
				throw new IllegalArgumentException("The field "+ field +" contained "+ parts.length +" parts.");
			}
		}

		if (unqualifiedFields.size() > 0) {
			String value = unqualifiedFields.get(0);
			if (etlFile.getFilename() != null) {
				System.out.println("Warning: overriding file="+ etlFile.getFilename() +" with "+ value);
			}
			etlFile.setFilename(value);
		}

		if (unqualifiedFields.size() > 1) {
			String value = unqualifiedFields.get(1);
			if (etlFile.getFileType() != null) {
				System.out.println("Warning: overriding type="+ etlFile.getFileType() +" with "+ value);
			}
			try {
				etlFile.setFileType(DataType.valueOf(DataType.class, value));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("The ETL file type \""+value+"\" does not exist. The supported filetypes are ["+ETLFileOld.SUPPORTED_FILETYPES_LIST+"]");
			}
		}

		if (unqualifiedFields.size() > 2) {
			String value = unqualifiedFields.get(2);
			if (etlFile.getTableName() != null) {
				System.out.println("Warning: overriding table="+ etlFile.getTableName() +" with "+ value);
			}
			etlFile.setTableName(value);
		}

		if (etlFile.getFilename() == null) {
			throw new IllegalArgumentException("File name is required.");
		}

		if (etlFile.getFileType() == null) {
			throw new IllegalArgumentException("Type is required.");
		}

		if (etlFile.getFileType() == DataType.user_defined && etlFile.getTableName() == null) {
			throw new IllegalArgumentException("Table name is required for user-defined type.");
		}

		return etlFile;
	}
}
