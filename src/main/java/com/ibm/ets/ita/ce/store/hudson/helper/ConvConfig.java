package com.ibm.ets.ita.ce.store.hudson.helper;

/*******************************************************************************
 * (C) Copyright IBM Corporation  2011, 2016
 * All Rights Reserved
 *******************************************************************************/

import java.util.ArrayList;

import com.ibm.ets.ita.ce.store.ActionContext;
import com.ibm.ets.ita.ce.store.conversation.model.GeneralItem;
import com.ibm.ets.ita.ce.store.model.CeInstance;

public class ConvConfig extends GeneralItem {
	public static final String copyrightNotice = "(C) Copyright IBM Corporation  2011, 2016";

	private static final String PROP_SPLITPHRASES = "split phrases";
	private static final String PROP_RUNRULES = "run rules";
	private static final String PROP_SINGANS = "single answers";
	private static final String PROP_PHRASEDELIMS = "phrase delimiters";
	private static final String PROP_SENDELIMS = "sentence delimiters";
	private static final String PROP_CLAUSEDELIMS = "clause delimiters";
	private static final String PROP_CLAUSEPUNCS = "clause punctuation";
	private static final String PROP_QSMS = "question start markers";
	private static final String PROP_QEMS = "question end markers";
	private static final String PROP_COMWORDS = "common words";
	private static final String PROP_NEGWORDS = "negation words";
	private static final String PROP_MARKER = "marker";
	private static final String PROP_MAXSUGGS = "max suggestions";
	private static final String PROP_MAXRESULTS = "max answer result rows";
	private static final String PROP_MAXDBROWS = "max database to ce rows";
	private static final String PROP_DEFANSCONF = "default answer confidence";
	private static final String PROP_DEFINTCONF = "default interpretation confidence";
	private static final String PROP_DEFABANSCONF = "default ability to answer confidence";
	private static final String PROP_COMPANSCONF = "compute answer confidence";
	private static final String PROP_COMPINTCONF = "compute interpretation confidence";
	private static final String PROP_COMPABANSCONF = "compute ability to answer confidence";

	private static final String VAL_TRUE = "true";
	
	private CeInstance ccInst = null;
	private boolean isSplittingPhrases = false;
	private boolean isRunningRules = false;
	private boolean isReturningSingleAnswers = false;
	private boolean computeInterpretationConfidence = false;
	private boolean computeAbilityToAnswerConfidence = false;
	private boolean computeAnswerConfidence = false;
	private ArrayList<String> phraseDelimiters = null;
	private ArrayList<String> senDelimiters = null;
	private ArrayList<String> clauseDelimiters = null;
	private ArrayList<String> clausePunctuation = null;
	private ArrayList<String> questionStartMarkers = null;
	private ArrayList<String> questionEndMarkers = null;
	private ArrayList<String> commonWords = null;
	private ArrayList<String> negationWords = null;
	private int maxSuggestions = 20;
	private int maxAnswerRows = 100;
	private int maxDatabaseRows = 100;
	private int defaultInterpretationConfidence = -1;
	private int defaultAbilityToAnswerConfidence = -1;
	private int defaultAnswerConfidence = -1;

	//Private to ensure static creator is used
	private ConvConfig(ActionContext pAc, CeInstance pInst) {
		this.ccInst = pInst;
		
		populateListsAndValues(pAc);
	}

	public static ConvConfig createUsing(ActionContext pAc, CeInstance pInst) {
		ConvConfig cc = new ConvConfig(pAc, pInst);

		return cc;
	}

	private void populateListsAndValues(ActionContext pAc) {
		this.phraseDelimiters = populateUsing(pAc, PROP_PHRASEDELIMS);
		this.senDelimiters = populateUsing(pAc, PROP_SENDELIMS);
		this.clauseDelimiters = populateUsing(pAc, PROP_CLAUSEDELIMS);
		this.clausePunctuation = populateUsing(pAc, PROP_CLAUSEPUNCS);
		this.questionStartMarkers = populateUsing(pAc, PROP_QSMS);
		this.questionEndMarkers = populateUsing(pAc, PROP_QEMS);
		this.commonWords = populateUsing(pAc, PROP_COMWORDS);
		this.negationWords = populateUsing(pAc, PROP_NEGWORDS);
		
		String maxSuggText = this.ccInst.getSingleValueFromPropertyNamed(PROP_MAXSUGGS);
		String maxResults = this.ccInst.getSingleValueFromPropertyNamed(PROP_MAXRESULTS);
		String maxDbRows = this.ccInst.getSingleValueFromPropertyNamed(PROP_MAXDBROWS);
		String defAnsConf = this.ccInst.getSingleValueFromPropertyNamed(PROP_DEFANSCONF);
		String defIntConf = this.ccInst.getSingleValueFromPropertyNamed(PROP_DEFINTCONF);
		String defAbAnsConf = this.ccInst.getSingleValueFromPropertyNamed(PROP_DEFABANSCONF);
		
		if (!maxSuggText.isEmpty()) {
			this.maxSuggestions = new Integer(maxSuggText).intValue();
		}
		
		if (!maxResults.isEmpty()) {
			this.maxAnswerRows = new Integer(maxResults).intValue();
		}

		if (!maxDbRows.isEmpty()) {
			this.maxDatabaseRows = new Integer(maxDbRows).intValue();
		}

		if (!defAnsConf.isEmpty()) {
			this.defaultAnswerConfidence = new Integer(defAnsConf).intValue();
		}

		if (!defIntConf.isEmpty()) {
			this.defaultInterpretationConfidence = new Integer(defIntConf).intValue();
		}

		if (!defAbAnsConf.isEmpty()) {
			this.defaultAbilityToAnswerConfidence = new Integer(defAbAnsConf).intValue();
		}

		if (this.ccInst.getSingleValueFromPropertyNamed(PROP_SPLITPHRASES).equalsIgnoreCase(VAL_TRUE)) {
			this.isSplittingPhrases = true;
		}

		if (this.ccInst.getSingleValueFromPropertyNamed(PROP_RUNRULES).equalsIgnoreCase(VAL_TRUE)) {
			this.isRunningRules = true;
		}

		if (this.ccInst.getSingleValueFromPropertyNamed(PROP_SINGANS).equalsIgnoreCase(VAL_TRUE)) {
			this.isReturningSingleAnswers = true;
		}

		if (this.ccInst.getSingleValueFromPropertyNamed(PROP_COMPANSCONF).equalsIgnoreCase(VAL_TRUE)) {
			this.computeAnswerConfidence = true;
		}

		if (this.ccInst.getSingleValueFromPropertyNamed(PROP_COMPINTCONF).equalsIgnoreCase(VAL_TRUE)) {
			this.computeInterpretationConfidence = true;
		}

		if (this.ccInst.getSingleValueFromPropertyNamed(PROP_COMPABANSCONF).equalsIgnoreCase(VAL_TRUE)) {
			this.computeAbilityToAnswerConfidence = true;
		}
	}

	private ArrayList<String> populateUsing(ActionContext pAc, String pPropName) {
		ArrayList<String> result = null;

		CeInstance relInst = this.ccInst.getSingleInstanceFromPropertyNamed(pAc, pPropName);

		if (relInst != null) {
			result = relInst.getValueListFromPropertyNamed(PROP_MARKER);
		}

		if (result == null) {
			result = new ArrayList<String>();
		}

		return result;
	}

	protected ArrayList<String> getPhraseDelimiterList() {
		return this.phraseDelimiters;
	}

	protected ArrayList<String> getSentenceDelimiterList() {
		return this.senDelimiters;
	}

	protected ArrayList<String> getClauseDelimiterList() {
		return this.clauseDelimiters;
	}

	protected ArrayList<String> getClausePunctuationList() {
		return this.clausePunctuation;
	}
	
	protected ArrayList<String> getQuestionEndMarkers() {
		return this.questionEndMarkers;
	}

	public ArrayList<String> getQuestionStartMarkers() {
		return this.questionStartMarkers;
	}
	
	public ArrayList<String> getCommonWords() {
		return this.commonWords;
	}

	public ArrayList<String> getNegationWords() {
		return this.negationWords;
	}

	public int getMaxSuggestions() {
		return this.maxSuggestions;
	}

	public int getMaxAnswerRows() {
		return this.maxAnswerRows;
	}

	public int getMaxDatabaseRows() {
		return this.maxDatabaseRows;
	}

	protected boolean isSplittingPhrases() {
		return this.isSplittingPhrases;
	}

	public boolean isRunningRules() {
		return this.isRunningRules;
	}
	
	public boolean isReturningSingleAnswers() {
		return this.isReturningSingleAnswers;
	}
	
	public boolean computeInterpretationConfidence() {
		return this.computeInterpretationConfidence;
	}

	public boolean computeAbilityToAnswerConfidence() {
		return this.computeAbilityToAnswerConfidence;
	}

	public boolean computeAnswerConfidence() {
		return this.computeAnswerConfidence;
	}

	public int defaultInterpretationConfidence() {
		return this.defaultInterpretationConfidence;
	}

	public int defaultAbilityToAnswerConfidence() {
		return this.defaultAbilityToAnswerConfidence;
	}

	public int defaultAnswerConfidence() {
		return this.defaultAnswerConfidence;
	}

}