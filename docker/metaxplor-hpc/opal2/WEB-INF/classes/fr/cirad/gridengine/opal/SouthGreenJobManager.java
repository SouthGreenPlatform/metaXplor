package fr.cirad.gridengine.opal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.ggf.drmaa.DrmaaException;
import org.ggf.drmaa.JobInfo;
import org.ggf.drmaa.JobTemplate;
import org.ggf.drmaa.Session;
import org.ggf.drmaa.SessionFactory;
import org.ggf.drmaa.UnsupportedAttributeException;
import org.globus.gram.GramJob;

import edu.sdsc.nbcr.opal.AppConfigType;
import edu.sdsc.nbcr.opal.StatusOutputType;
import edu.sdsc.nbcr.opal.manager.JobManagerException;
import edu.sdsc.nbcr.opal.manager.OpalJobManager;

/**
 * 
 * Implementation of an Opal Job Manager using DRMAA
 */
public class SouthGreenJobManager implements OpalJobManager {

	// get an instance of a log4j logger
	private static Logger logger = Logger.getLogger(SouthGreenJobManager.class.getName());
	private Properties props; // the container properties being passed
	private AppConfigType config; // the application configuration
	private StatusOutputType status; // current status
	private String handle; // the DRMAA job id for this submission
	private boolean started = false; // whether the execution has started
//	private volatile boolean done = false; // whether the execution is complete
	private static Session session = null; // drmaa session
	private JobInfo jobInfo; // job information returned after completion by DRMAA
	private static final long JOB_OUTPUT_EXPIRATION_DELAY_MILLIS = 1000*60*60*24*15;	/* 15 days */
	
	static {
		SessionFactory factory = SessionFactory.getFactory();
		session = factory.getSession();
		try {
			session.init(null);
		} catch (DrmaaException de) {
			logger.fatal("Can't initialize DRMAA session: " + de.getMessage());
		}
	}

	/**
	 * Initialize the Job Manager for a particular job
	 * 
	 * @param props
	 *            the properties file containing the value to configure this
	 *            plugin
	 * @param config
	 *            the opal configuration for this application
	 * @param handle
	 *            manager specific handle to bind to, if this is a resumption.
	 *            NULL,if this manager is being initialized for the first time.
	 * 
	 * @throws JobManagerException
	 *             if there is an error during initialization
	 */
	public void initialize(Properties props, AppConfigType config, String handle) throws JobManagerException {
		logger.info("called");
		this.props = props;
		this.config = config;
		this.handle = handle;
		// initialize status
		status = new StatusOutputType();
	}

	/**
	 * General clean up, if need be
	 * 
	 * @throws JobManagerException
	 *             if there is an error during destruction
	 */
	public void destroyJobManager() throws JobManagerException {
		logger.info("called");
		// TODO: not sure what needs to be done here
		throw new JobManagerException("destroyJobManager() method not implemented");
	}

	/**
	 * Launch a job with the given arguments. The input files are already staged
	 * in by the service implementation, and the plug in can assume that they
	 * are already there
	 * 
	 * @param argList
	 *            a string containing the command line used to launch the
	 *            application
	 * @param numProcs
	 *            the number of processors requested. Null, if it is a serial
	 *            job
	 * @param workingDir
	 *            String representing the working directory of this job on the
	 *            local system
	 * 
	 * @return a plugin specific job handle to be persisted by the service
	 *         implementation
	 * @throws JobManagerException
	 *             if there is an error during job launch
	 */
	public String launchJob(String argList, Integer numProcs, final String workingDir) throws JobManagerException {
		logger.info("called");
		
		new Thread()
		{
			public void run()
			{
				try
				{
					cleanupOldJobResults(workingDir + File.separator + ".." + File.separator);
				}
				catch (IOException e1)
				{
					logger.error("Unable to cleanup old job results" + e1);
				}
			}
		}.start();
		
		File invocationContext = new File(workingDir + "context.txt");
//		if (!invocationContext.exists() || invocationContext.length() == 0)
//			throw new JobManagerException("You must provide an input file named context.txt containing details to log about the invocation's origin");
		
		// make sure we have all parameters we need
		if (config == null) {
			String msg = "Can't find application configuration - " + "Plugin not initialized correctly";
			logger.error(msg);
			throw new JobManagerException(msg);
		}
		// create list of arguments
		String args = config.getDefaultArgs();
		if (args == null) {
			args = argList;
		} else {
			String userArgs = argList;
			if (userArgs != null)
				args += " " + userArgs;
		}
		if (args != null) {
			args = args.trim();
		}
		logger.debug("Argument list: " + args);
		// get the number of processors available
		String systemProcsString = props.getProperty("num.procs");
		int systemProcs = 0;
		if (systemProcsString != null) {
			systemProcs = Integer.parseInt(systemProcsString);
		}
		// launch executable using DRMAA
		String cmd = null;
		String[] argsArray = null;
		if (config.isParallel()) {
			// make sure enough processors are present for the job
			if (numProcs == null) {
				String msg = "Number of processes unspecified for parallel job";
				logger.error(msg);
				throw new JobManagerException(msg);
			} else if (numProcs.intValue() > systemProcs) {
				String msg = "Processors required - " + numProcs + ", available - " + systemProcs;
				logger.error(msg);
				throw new JobManagerException(msg);
			}
			// check if the mpi.run property is set
			String mpiRun = props.getProperty("mpi.run");
			if (mpiRun == null) {
				String msg = "Can't find property mpi.run for running parallel job";
				logger.error(msg);
				throw new JobManagerException(msg);
			}
			// create command string and arguments for parallel run
			cmd = "/bin/sh";
			// append arguments - needs to be this way to locate machinefile
			String newArgs = mpiRun + " -machinefile $TMPDIR/machines" + " -np " + numProcs + " " + config.getBinaryLocation();
			if ((args != null) && (!(args.equals("")))) {
				args = newArgs + " " + args;
			} else {
				args = newArgs;
			}
			logger.debug("CMD: " + args);
			// construct the args array
			argsArray = new String[] { "-c", args };
		} else {
			// create command string and arguments for serial run
			cmd = config.getBinaryLocation();
			if (args == null)
				args = "";
			logger.debug("CMD: " + cmd + " " + args);
			// construct the args array
			if (!args.equals("")) {
				argsArray = args.split("[\\s]+");
			} else {
				argsArray = new String[] {};
			}
		}
		// get the parallel environment being used
		String drmaaPE = null;
		if (config.isParallel()) {
			String appDrmaaPE = config.getDrmaaPE();
			if ((appDrmaaPE != null) && !appDrmaaPE.equals("")) {
				drmaaPE = appDrmaaPE;
				logger.debug("Drmaa parallel environment defined at app level");
			} else {
				drmaaPE = props.getProperty("drmaa.pe");
				logger.debug("Drmaa parallel environment defined at server level");
			}
			logger.debug("Using drmaa parallel environment " + drmaaPE);
			if (drmaaPE == null) {
				String msg = "Can't find property drmaa.pe for running parallel job";
				logger.error(msg);
				throw new JobManagerException(msg);
			}
		}
		// get the drmaa queue
		String drmaaQueue = null;
		String appDrmaaQueue = config.getDrmaaQueue();
		if ((appDrmaaQueue != null) && !appDrmaaQueue.equals("")) {
			drmaaQueue = appDrmaaQueue;
			logger.debug("Drmaa parallel queue at app level");
		} else {
			drmaaQueue = props.getProperty("drmaa.queue");
			logger.debug("Drmaa parallel queue at server level");
		}
		logger.debug("Using drmaa queue " + drmaaQueue);
		// get the hard run limit
		long hardLimit = 0;
		if ((props.getProperty("opal.hard_limit") != null)) {
			hardLimit = Long.parseLong(props.getProperty("opal.hard_limit"));
			logger.info("All jobs have a hard limit of " + hardLimit + " seconds");
		}
		// launch the job using the above information
		try {
			logger.debug("Working directory: " + workingDir);
			JobTemplate jt = session.createJobTemplate();
			String nativeSpec = "";
			if ((drmaaQueue != null) && !drmaaQueue.equals("")) {
				nativeSpec += "-q " + drmaaQueue + " ";
			}
			if (config.isParallel()) {
				nativeSpec += "-pe " + drmaaPE + " " + numProcs;
			}
			nativeSpec += " -shell yes ";
			jt.setNativeSpecification(nativeSpec);
			jt.setRemoteCommand(cmd);
			jt.setArgs(Arrays.asList(argsArray));
			jt.setWorkingDirectory(workingDir);
			
			String sErrorFilePath = workingDir + "stderr.txt", sOutputFilePath = workingDir  + "stdout.txt";
			
			// we must create both files because if one of them does not exist then edu.sdsc.nbcr.opal.AppServiceImpl will complain
			FileOutputStream fos = new FileOutputStream(new File(sErrorFilePath));
			fos.close();
			fos = new FileOutputStream(new File(sOutputFilePath));
			fos.close();
			
			jt.setErrorPath(":" + workingDir + "/stderr.txt");
			jt.setOutputPath(":" + workingDir + "/stdout.txt");
			if (hardLimit != 0) {
				try {
					jt.setHardRunDurationLimit(hardLimit);
				} catch (UnsupportedAttributeException e) {
					String msg = "Can't set hard limit - " + e.getMessage();
					logger.error(msg);
					// not fatal - continue
				}
			}
			handle = session.runJob(jt);
			logger.info("DRMAA job has been submitted with id " + handle);
			
			File invocationDetailFolder = new File(workingDir + ".." + File.separator + "invocation_details");
			if (!invocationDetailFolder.exists() && !invocationDetailFolder.mkdirs())
				throw new JobManagerException("Unable to create folder: " + invocationDetailFolder.getAbsolutePath());
			String[] splittedWorkingDir = workingDir.split(File.separator);
			if (invocationContext.exists() && invocationContext.length() > 0)
				invocationContext.renameTo(new File(invocationDetailFolder.getAbsolutePath() + File.separator + handle + "_" + splittedWorkingDir[splittedWorkingDir.length - 1] + ".txt"));
			
			session.deleteJobTemplate(jt);
		} catch (Exception e) {
			String msg = "Error while running executable via DRMAA - " + e.getMessage();
			logger.error(msg);
			throw new JobManagerException(msg);
		}
		// job has been started
		started = true;
		// return an identifier for this process
		return handle;
	}

	/**
	 * Block until the job state is GramJob.STATUS_ACTIVE
	 * 
	 * @return status for this job after blocking
	 * @throws JobManagerException
	 *             if there is an error while waiting for the job to be ACTIVE
	 */
	public StatusOutputType waitForActivation() throws JobManagerException {
		logger.info("called");
		// check if this process has been started already
		if (!started) {
			String msg = "Can't wait for a process that hasn't be started";
			logger.error(msg);
			throw new JobManagerException(msg);
		}
		// poll till status is RUNNING
		try {
			while ((session.getJobProgramStatus(handle) == Session.QUEUED_ACTIVE) || (session.getJobProgramStatus(handle) == Session.SYSTEM_ON_HOLD) || (session.getJobProgramStatus(handle) == Session.USER_ON_HOLD) || (session.getJobProgramStatus(handle) == Session.USER_SYSTEM_ON_HOLD)) {
				try {
					// sleep for 3 seconds
					Thread.sleep(3000);
				} catch (InterruptedException ie) {
					// minor exception - log exception and continue
					logger.error(ie.getMessage());
					continue;
				}
			}
		} catch (DrmaaException de) {
			String msg = "Can't get status for DRMAA job: " + handle;
			logger.error(msg, de);
			throw new JobManagerException(msg + " - " + de.getMessage());
		}
		// update status to active
		status.setCode(GramJob.STATUS_ACTIVE);
		status.setMessage("Execution in progress");
		return status;
	}

	/**
	 * Block until the job finishes executing
	 * 
	 * @return final job status
	 * @throws JobManagerException
	 *             if there is an error while waiting for the job to finish
	 */
	public StatusOutputType waitForCompletion() throws JobManagerException {
		logger.info("called");
		// check if this process has been started already
		if (!started) {
			String msg = "Can't wait for a process that hasn't be started";
			logger.error(msg);
			throw new JobManagerException(msg);
		}
		// wait till the process finishes, and get final status
		try {
			jobInfo = session.wait(handle, Session.TIMEOUT_WAIT_FOREVER);
		} catch (DrmaaException de) {
			String msg = "Exception while waiting for process to finish";
			logger.error(msg, de);
			throw new JobManagerException(msg + " - " + de.getMessage());
		}
		// update status
		int exitValue;
		try {
			exitValue = jobInfo.getExitStatus();
		} catch (Exception e) {
			logger.error("Can't get exit value from DRMAA - setting it to 100");
			exitValue = 100;
		}
		if (exitValue == 0) {
			status.setCode(GramJob.STATUS_DONE);
			status.setMessage("Execution complete - " + "check outputs to verify successful execution");
		} else {
			status.setCode(GramJob.STATUS_FAILED);
			status.setMessage("Execution failed - process exited with value " + exitValue);
		}
		return status;
	}

	/**
	 * Destroy this job
	 * 
	 * @return final job status
	 * @throws JobManagerException
	 *             if there is an error during job destruction
	 */
	public StatusOutputType destroyJob() throws JobManagerException {
		logger.info("called");
		// check if this process has been started already
		if (!started) {
			String msg = "Can't destroy a process that hasn't be started";
			logger.error(msg);
			throw new JobManagerException(msg);
		}
		// destroy process
		try {
			session.control(handle, Session.TERMINATE);
		} catch (DrmaaException de) {
			String msg = "Exception while trying to destroy process";
			logger.error(msg, de);
			throw new JobManagerException(msg + " - " + de.getMessage());
		}
		// update status
		status.setCode(GramJob.STATUS_FAILED);
		status.setMessage("Process destroyed on user request");
		return status;
	}
	
	private void cleanupOldJobResults(String outputFolderContainer) throws IOException
	{
		long nowMillis = new Date().getTime();
		for (File f : new File(outputFolderContainer).listFiles())
			if (f.isDirectory() && f.getName().startsWith("app") && nowMillis - f.lastModified() > JOB_OUTPUT_EXPIRATION_DELAY_MILLIS)
			{
				FileUtils.deleteDirectory(f);	// it is an expired job-output-folder
				logger.info("expired job-output-folder was deleted: " + f.getPath());
			}
	}
}

