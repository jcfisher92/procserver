/**
 * 
 */
package com.emerywaterhouse.edb;

import com.emerywaterhouse.server.ProcessServer;

/**
 * @author JFisher
 *
 */
public class DbJobProc implements Runnable {
	private static long startId = 1;

	protected EDBApp m_App; // Pointer to the process application instance.
	protected String m_CurProcStatus; // A buffer for the current processing
										// data.
	protected long m_Id; // An id for identifying an instance of a bod
							// processor.
	protected long m_Interval; // The interval to sleep when not processing
	protected Thread m_Thread; // The thread that runs the processing.
	protected long m_MaxRunTime; // The maximum amount of time the job should
									// run without throwing an error.
	protected String m_Name; // The name of the processor.
	protected long m_RunTime; // The length of time this job has been actively
								// running.
	protected long m_StartTime; // The time this job was started.
	protected long m_LastStartTime; // the last time this job was started successfully
	protected short m_Status; // The current status of the job

	/**
	* 
	*/
	public DbJobProc() {
		m_Id = startId++;
		m_Name = String.format("%s-%d", this.getClass().getSimpleName(), m_Id);

		m_Thread = new Thread(this, m_Name);
		m_Thread.setDaemon(true);
		m_Status = ProcessServer.init;
		m_CurProcStatus = "initializing";
	}

	/**
	 * 
	 * @param app
	 * @param name
	 */
	public DbJobProc(EDBApp app, String name) {
		this();

		try {
			setApplication(app);
			setName(name);
		}

		catch (Exception ex) {
			ProcessServer.log.error(String.format("[%s]", m_Name), ex);
		}
	}

	/**
	 * Base class method for doing the actual work. Descendant classes should
	 * override this method.
	 */
	protected void doProcessing() {
		;
	}

	/**
	 * Return the id of the processor.
	 *
	 * @return The internal processor id used for identify this process.
	 */
	public long getId() {
		return m_Id;
	}

	/**
	 * 
	 * @return The job interval in minutes
	 */
	public long getInterval() {
		return m_Interval / ProcessServer.minute;
	}

	/**
	 * 
	 * @return The last successful start time
	 */
	public long getLastStartTime() {
		return m_LastStartTime;
	}
	
	/**
	 * The maximum amount of time to run before an error occurs.
	 * 
	 * @return The run time in minutes.
	 */
	public long getMaxRunTime() {
		return m_MaxRunTime / ProcessServer.minute;
	}

	/**
	 * 
	 * @return
	 */
	public String getName() {
		return m_Name;
	}

	/**
	 * 
	 * @return
	 */
	public synchronized String getProcessStatus() {
		return m_CurProcStatus;
	}

	/**
	 * @return The length of time this process has been running in seconds. If
	 *         the process is idle, the last know runtime is returned.
	 */
	public long getRunTime() {
		if (m_Status == ProcessServer.running)
			m_RunTime = (System.currentTimeMillis() - getStartTime()) / ProcessServer.second;

		return m_RunTime;
	}

	/**
	 * 
	 * @return
	 */
	public long getStartTime() {
		return m_StartTime;
	}

	/**
	 * 
	 * @return
	 */
	public short getStatus() {
		return m_Status;
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		StringBuffer logMsg = new StringBuffer();

		if (Thread.currentThread() == m_Thread) {
			logMsg.append(String.format("[%s] started", m_Name));
			ProcessServer.log.info(logMsg.toString());

			try {
				while (m_Status != ProcessServer.stopped) {
					try {
						m_Status = ProcessServer.running;
						m_StartTime = System.currentTimeMillis();
						doProcessing();

						if (m_Status != ProcessServer.stopped) {
							m_Status = ProcessServer.idle;
							m_StartTime = 0;
							m_LastStartTime = System.currentTimeMillis();
							Thread.sleep(m_Interval);
						}
					}

					catch (InterruptedException ex) {
						if (ex instanceof InterruptedException) {
							Thread.currentThread().interrupt();
							break;
						} else {
							ProcessServer.log.error(String.format("[%s]", m_Name), ex);

							logMsg.setLength(0);
							logMsg.append(String.format("[%s\r\n]", m_Name));
							logMsg.append(
									"There was an exception during processing.  See ../log/server.log for the stacktrace\r\n");

							logMsg.append("The exception was:\r\n");
							logMsg.append(ex.getMessage());

							ProcessServer.getInstance().notifyMis(logMsg.toString());
						}
					}
				}
			}

			finally {
				if (m_App != null)
					m_App.remJobProc(this);
			}
		} else {
			logMsg.setLength(0);
			logMsg.append(String.format("[%s] should be started by calling the start method", m_Name));
			ProcessServer.log.error(logMsg.toString());
		}

		logMsg.setLength(0);
		logMsg.append(String.format("[%s] stopped", m_Name));
		ProcessServer.log.info(logMsg.toString());
		logMsg = null;
	}

	/**
	 * Sets the parent application var.
	 *
	 * @param app
	 *            A reference to the parent application object.
	 * @throws Exception
	 *             when the monitor var is null.
	 */
	public void setApplication(EDBApp app) throws Exception {
		if (app != null) {
			m_App = app;
			m_App.addJobProc(this);
		} else
			throw new Exception("[DbJobProcessor] parent application can't be set to null");
	}

	/**
	 * Sets the interval between job runs.
	 * 
	 * @param interval
	 *            The time in minutes
	 */
	public void setInterval(int interval) {
		m_Interval = interval * ProcessServer.minute;
	}

	/**
	 * Sets the maximum amount of time to run before an error occurs.
	 * 
	 * @param maxRunTime
	 *            The time in minutes.
	 */
	public void setMaxRunTime(int maxRunTime) {
		m_MaxRunTime = maxRunTime * ProcessServer.minute;
	}

	/**
	 * 
	 * @param name
	 */
	public void setName(String name) {
		m_Name = name;
	}

	/**
	 * Signal the process to stop what it's doing.
	 */
	public void stop() {
		m_Status = ProcessServer.stopped;
		m_Thread.interrupt();
	}

	/**
	 * Signal the process to stop what it's doing.
	 */
	public synchronized void stopIdle() {
		m_Status = ProcessServer.idle;
	}

	/**
	 * Starts the processing of the job by starting the thread or by calling the
	 * run method directly.
	 * 
	 * @param threaded
	 *            flag for threaded or non threaded processing.
	 */
	public void startProcessing(boolean threaded) {
		if (threaded) {
			if (!m_Thread.isAlive())
				m_Thread.start();
			else {
				m_Thread.interrupt();
			}
		} else
			doProcessing();
	}
}
