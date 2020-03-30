/**
 * Title:        FascorMsg<p>
 * Description:  This is the base class for fascor messaging.  The class is a java bean component
 *               that is serializable.  It does not implement the EmObject interface however.<p>
 * 
 * Company:      Emery-Waterhouse<p>
 * @author Jeffrey Fisher
 * 
 * Create Date: ?
 * Last Update: $Id: FascorMsg.java,v 1.4 2008/10/31 17:16:05 jfisher Exp $
 * 
 * History:
 *    07/26/2004 Removed some unused imports - jcf
 */
package com.emerywaterhouse.fas;

import java.util.*;
import java.text.*;

//
// Bean stuff
import java.io.Serializable;

//
// The base class for which all other fascor messages are derived.  This class is a java
// bean and can be used as a component in other apps.
public class FascorMsg implements Serializable {
	static final long serialVersionUID = 2658732232081307623L;
	protected final int MAX_MSG_LEN = 1000;

	protected String m_Batch;
	protected StringBuffer m_Msg;
	protected int m_MsgLen;
	protected String m_Trans;
	protected boolean m_Processed;
	protected int m_Seq;
	protected Date m_UpdateDate;
	protected String m_Pid;
	protected String m_Uid;
	protected GregorianCalendar m_Cal;
	protected NumberFormat m_Fmt;

	/**
	 * Default constructor. Creates an empty message and fills the message
	 * buffer with spaces.
	 *
	 * Note - The reset method is called which contains a call to initMsg. We
	 * need to make sure reset and initMsg are both called. Reset conflicts with
	 * the other constructor.
	 * 
	 * @throws Exception
	 */
	public FascorMsg() throws Exception {
		m_MsgLen = MAX_MSG_LEN;
		m_Msg = new StringBuffer();
		m_UpdateDate = new Date();
		m_Cal = new GregorianCalendar();

		setNumberFormat();
		reset();
	}

	//
	// Creates a message object from the message string.
	public FascorMsg(String msg) throws Exception {
		if (msg != null) {
			m_UpdateDate = new Date();
			m_Cal = new GregorianCalendar();
			m_MsgLen = msg.length();
			m_Msg = new StringBuffer(msg);

			setNumberFormat();
			initMsg();
			parseMsg();
		} else
			throw new Exception("null msg");
	}

	//
	// Clean up and release any resources.
	public void finalize() throws Throwable {
		m_Msg = null;
		m_Trans = null;
		m_Batch = null;
		m_UpdateDate = null;
		m_Cal = null;
		m_Fmt = null;

		super.finalize();
	}

	//
	// Assigns one message to another. Can be used to assign a message of the
	// base type
	// to a descendant type.
	public void assign(FascorMsg msg) throws Exception {
		if (msg != null) {
			m_Seq = msg.m_Seq;
			m_UpdateDate = msg.m_UpdateDate;
			setMsg(msg.getMsg());
			setTrans(msg.m_Trans);
			setBatch(msg.m_Batch);
			setProcessed(msg.m_Processed);
			setPid(msg.m_Pid);
			setUid(msg.m_Uid);
		}
	}

	//
	// returns the batch number for the message.
	public final String getBatch() {
		return m_Batch;
	}

	//
	// Place holder for derived classes to implement.
	public String getFacilityNbr() {
		return null;
	}

	//
	// Returns the fascor msg string.
	public final String getMsg() {
		return m_Msg.toString();
	}

	//
	// return the pid used by this message
	public final String getPid() {
		return m_Pid;
	}

	//
	// Returns the processed flag.
	public boolean getProcessed() {
		return m_Processed;
	}

	//
	// Returns the sequence number
	public int getSeq() {
		return m_Seq;
	}

	//
	// Returns the trans number.
	public final String getTrans() {
		return m_Trans;
	}

	//
	// returns the user id that will be sent to fascor when the
	// message is sent.
	public final String getUid() {
		return m_Uid;
	}

	//
	// Initializes the fascor message object with the basic information needed
	// to send a
	// message to fascor.
	public void initMsg() throws Exception {
		setProcessed(false);
		setUid("EIS_EMERY");
		setPid("EMERYOPD");
	}

	//
	// Parses a message string and sets the properties of the message.
	// Just a place holder in the base class.
	//
	// Notes -
	// 1. The function uses the internal string buffer for parsing.
	// 2. Because fascor messages are inconsistant, we can't parse
	// anything here. Descendant classes should do the work.
	protected void parseMsg() throws Exception {
		if (m_Msg == null)
			throw new Exception("null message buffer");
	}

	/**
	 * Clears any old data and resets the message buffer to be blanks again.
	 * Also used to initialize a new message. This method is called from the
	 * default constructor to initialize input messages that will go to fascor.
	 *
	 * @throws Exception
	 *             - when an exception occurs in the initMsg method.
	 */
	//
	public void reset() throws Exception {
		char[] Filler = new char[m_MsgLen];

		try {
			if (m_Msg != null) {
				Arrays.fill(Filler, ' ');
				m_Msg.setLength(0);
				m_Msg.append(Filler);
			}

			m_Trans = "";
			m_Batch = "";

			initMsg();
		}

		finally {
			Filler = null;
		}
	}

	//
	// Sets the batch number of the msg.
	//
	// Params:
	// batch - if not null, some form of identifier that identifies a group of
	// transactions
	// If batch is null, then the batch is the current time in milliseconds
	// truncated to 7
	// characters for the fascor inbound table.
	public void setBatch(String batch) {
		String Tmp;

		if (batch != null) {
			if (batch.length() > 8)
				m_Batch = batch.substring(0, 7);
			else
				m_Batch = batch;
		} else {
			Tmp = Long.toString(System.currentTimeMillis());
			m_Batch = Tmp.substring(0, 7);
		}
	}

	//
	// Sets the Fascor message string.
	public void setMsg(String msg) throws Exception {
		if (msg != null) {
			m_Msg = null;
			m_Msg = new StringBuffer(msg);
			m_MsgLen = msg.length();
			parseMsg();
		}
	}

	//
	// Setup the format for fascor numbers which are a total 8 wide. The decimal
	// is implied.
	// This can be overridden by descendant classes.
	protected void setNumberFormat() {
		m_Fmt = NumberFormat.getNumberInstance();
		m_Fmt.setMaximumFractionDigits(0);
		m_Fmt.setMaximumIntegerDigits(8);
		m_Fmt.setMinimumFractionDigits(0);
		m_Fmt.setMinimumIntegerDigits(8);
		m_Fmt.setGroupingUsed(false);
	}

	//
	// Sets the process id that will be sent to fascor.
	// Max pid length is 11 characters.
	public void setPid(String pid) {
		if (pid != null) {
			if (pid.length() > 11)
				m_Pid = pid.substring(0, 10);
			else
				m_Pid = pid;
		}
	}

	//
	// Sets the processed flag
	public void setProcessed(boolean processed) {
		m_Processed = processed;
	}

	//
	// Sets the sequence field
	public void setSeq(int seq) {
		m_Seq = seq;
	}

	//
	// Sets the message transaction number. See fascor docs for trans numbers.
	// Transaction numbers have to be 6 characters or less.
	public void setTrans(String trans) throws Exception {
		if (trans != null) {
			if (trans.length() > 6)
				m_Trans = trans.substring(0, 5);
			else
				m_Trans = trans;

			m_Msg.replace(0, 4, trans);
		} else {
			m_Trans = "";
			m_Msg.replace(0, 4, "    ");
		}
	}

	//
	// Sets the user id for the fascor message
	// Max length is 11 characters
	public void setUid(String uid) {
		if (uid != null) {
			if (uid.length() > 11)
				m_Uid = uid.substring(0, 10);
			else
				m_Uid = uid;
		}
	}
}