package com.ibm.ets.ita.ce.store.hudson.helper;

/*******************************************************************************
 * (C) Copyright IBM Corporation  2011, 2016
 * All Rights Reserved
 *******************************************************************************/

import com.ibm.ets.ita.ce.store.conversation.model.ProcessedWord;

public class Interpretation {
	public static final String copyrightNotice = "(C) Copyright IBM Corporation  2011, 2016";

	private ProcessedWord processedWord = null;

	private Interpretation(ProcessedWord pPw) {
		//Private to enforce use of static creators
		this.processedWord = pPw;
	}

	public static Interpretation createUsing(ProcessedWord pPw) {
		return new Interpretation(pPw);
	}

	public String textSummary() {
		return this.processedWord.toString();
	}

}