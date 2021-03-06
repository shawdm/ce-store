package com.ibm.ets.ita.ce.store.hudson.helper;

/*******************************************************************************
 * (C) Copyright IBM Corporation  2011, 2016
 * All Rights Reserved
 *******************************************************************************/

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import com.ibm.ets.ita.ce.store.ActionContext;
import com.ibm.ets.ita.ce.store.client.web.ServletStateManager;
import com.ibm.ets.ita.ce.store.conversation.model.ProcessedWord;
import com.ibm.ets.ita.ce.store.hudson.handler.GenericHandler;
import com.ibm.ets.ita.ce.store.model.CeConcept;
import com.ibm.ets.ita.ce.store.model.CeInstance;
import com.ibm.ets.ita.ce.store.model.CeProperty;

public class WordCheckerCache {
	public static final String copyrightNotice = "(C) Copyright IBM Corporation  2011, 2016";
	
	private static final String CON_PROPCON = "property concept";
	private static final String CON_LINGTHING = "linguistic thing";
//	private static final String CON_ENTCON = "entity concept";

	private static final String PROP_PROPNAME = "property name";
	private static final String PROP_PLURAL = "plural form";
//	private static final String PROP_EXPBY = "is expressed by";
//	private static final String PROP_PLURAL = "plural form";
//	private static final String PROP_PAST = "past tense";

	private HashMap<String, CeConcept> matchingConcepts = new HashMap<String, CeConcept>();
	private HashMap<String, TreeMap<String, CeProperty>> matchingRelations = new HashMap<String, TreeMap<String, CeProperty>>();
	private HashMap<String, ArrayList<CeInstance>> matchingInstances = new HashMap<String, ArrayList<CeInstance>>();
	
//	private HashMap<String, TreeMap<String, CeConcept>> referredExactConcepts = new HashMap<String, TreeMap<String, CeConcept>>();

	private ArrayList<String> commonWords = null;
	private ArrayList<String> negationWords = null;
	private ArrayList<CeInstance> lingThingInsts = null;
	private HashMap<String, ArrayList<CeInstance>> lingThingPluralFormInsts = new HashMap<String, ArrayList<CeInstance>>();
	
	public synchronized void checkForMatchingConcept(ActionContext pAc, ProcessedWord pWord) {
		String cacheKey = pWord.getDeclutteredText();
		CeConcept tgtCon = null;
		
		if (!this.matchingConcepts.containsKey(cacheKey)) {
//			reportDebug("Looking live for concept using: " + cacheKey, pAc);
			tgtCon = pAc.getModelBuilder().getConceptNamed(pAc, cacheKey);
			this.matchingConcepts.put(cacheKey, tgtCon);
		} else {
			tgtCon = this.matchingConcepts.get(cacheKey);
		}

		pWord.setMatchingConcept(tgtCon);
	}

	public synchronized void checkForMatchingRelation(ActionContext pAc, ProcessedWord pWord) {
		String cacheKey = pWord.getDeclutteredText();
		TreeMap<String, CeProperty> tgtProps = null;
		
		if (!this.matchingRelations.containsKey(cacheKey)) {
//			reportDebug("Looking live for property using: " + cacheKey, pAc);
			tgtProps = new TreeMap<String, CeProperty>();

			for (CeInstance thisInst : pAc.getModelBuilder().getAllInstancesForConceptNamed(pAc, CON_PROPCON)) {
				String propFullName = thisInst.getInstanceName();

				for (String expVal : thisInst.getValueListFromPropertyNamed(PROP_PROPNAME)) {
					if (expVal.equals(cacheKey)) {
						CeProperty tgtProp = pAc.getModelBuilder().getPropertyFullyNamed(propFullName);
						tgtProps.put(tgtProp.formattedFullPropertyName(), tgtProp);
					}
				}
			}

			this.matchingRelations.put(cacheKey, tgtProps);
		} else {
			tgtProps = this.matchingRelations.get(cacheKey);
		}

		pWord.setMatchingRelations(tgtProps);
	}

	public synchronized void checkForMatchingInstances(ActionContext pAc, ProcessedWord pWord) {
		String cacheKey = pWord.getDeclutteredText();
		ArrayList<CeInstance> tgtInsts = null;
		HudsonManager hm = ServletStateManager.getHudsonManager(pAc);

		if (!this.matchingInstances.containsKey(cacheKey)) {
//			reportDebug("Looking live for instance using: " + cacheKey, pAc);
			tgtInsts = new ArrayList<CeInstance>();
			ArrayList<CeInstance> possInsts = hm.getIndexedEntityAccessor(pAc.getModelBuilder()).getInstancesNamedOrIdentifiedAs(pAc, cacheKey);

			for (CeInstance possInst : possInsts) {
				if (isValidMatchingInstance(pAc, possInst)) {	
					tgtInsts.add(possInst);
				}
			}

			this.matchingInstances.put(cacheKey, tgtInsts);
		} else {
			tgtInsts = this.matchingInstances.get(cacheKey);
		}

		if (!tgtInsts.isEmpty()) {
			pWord.setMatchingInstances(tgtInsts);
		}
	}

	public synchronized ArrayList<CeInstance> checkForMatchingInstances(ActionContext pAc, String pText) {
		ArrayList<CeInstance> tgtInsts = null;
		HudsonManager hm = ServletStateManager.getHudsonManager(pAc);

		if (!this.matchingInstances.containsKey(pText)) {
//			reportDebug("Looking live for instance using: " + pText, pAc);
			tgtInsts = new ArrayList<CeInstance>();
			ArrayList<CeInstance> possInsts = hm.getIndexedEntityAccessor(pAc.getModelBuilder()).getInstancesNamedOrIdentifiedAs(pAc, pText);

			for (CeInstance possInst : possInsts) {
				if (isValidMatchingInstance(pAc, possInst)) {	
					tgtInsts.add(possInst);
				}
			}

			this.matchingInstances.put(pText, tgtInsts);
		} else {
			tgtInsts = this.matchingInstances.get(pText);
		}

		return tgtInsts;
	}

	private static boolean isValidMatchingInstance(ActionContext pAc, CeInstance pInst) {
		boolean result = false;

		if (pInst != null) {
			result = !pInst.isOnlyMetaModelInstance() && !isUninterestingInstance(pAc, pInst) && !isOnlyConfigCon(pAc, pInst);
		}

		return result;
	}

	public static boolean isOnlyConfigCon(ActionContext pAc, CeInstance pTgtInst) {
		boolean result = true;

		for (CeConcept thisCon : pTgtInst.getDirectConcepts()) {
			if (!thisCon.hasParentNamed(GenericHandler.CON_CONFCON)) {
				result = false;
				break;
			}
		}

		return result;
	}

	private static boolean isUninterestingInstance(ActionContext pAc, CeInstance pInst) {
		return pInst.isConceptNamed(pAc, GenericHandler.CON_UNINTCON);
	}

	public synchronized ArrayList<String> getCommonWords(ConvConfig pCc, ActionContext pAc) {
		if (pCc != null) {
			if (this.commonWords == null) {
				this.commonWords = new ArrayList<String>();

				for (String thisCw : pCc.getCommonWords()) {
					String cwText = thisCw.trim().toLowerCase();
					this.commonWords.add(cwText);
				}
			}
		}

		return this.commonWords;
	}

	public synchronized ArrayList<String> getNegationWords(ConvConfig pCc, ActionContext pAc) {
		if (pCc != null) {
			if (this.negationWords == null) {
				this.negationWords = new ArrayList<String>();

				for (String thisNw : pCc.getNegationWords()) {
					String nwText = thisNw.trim().toLowerCase();

					this.negationWords.add(nwText);
				}
			}
		}

		return this.negationWords;
	}

	public synchronized ArrayList<CeInstance> getLingThingInstances(ActionContext pAc) {
		if (this.lingThingInsts == null) {
//			reportDebug("Creating live lingThing list", pAc);

			this.lingThingInsts = new ArrayList<CeInstance>();

			for (CeInstance thisInst : pAc.getModelBuilder().getAllInstancesForConceptNamed(pAc, CON_LINGTHING)) {
				if (!thisInst.isMetaModelInstance()) {
					this.lingThingInsts.add(thisInst);
				}
			}
		}

		return this.lingThingInsts;
	}

	public synchronized ArrayList<CeInstance> getLingThingPluralForms(ActionContext pAc, String pTgtText) {
		if (!this.lingThingPluralFormInsts.containsKey(pTgtText)) {
//			reportDebug("Creating live lingThing plural form list", pAc);
//			int totCount = 0;
			ArrayList<CeInstance> matchedInsts = new ArrayList<CeInstance>();

			for (CeInstance lingInst : pAc.getModelBuilder().getAllInstancesForConceptNamed(pAc, CON_LINGTHING)) {
				if (!lingInst.isMetaModelInstance()) {
					for (String thisPf : lingInst.getValueListFromPropertyNamed(PROP_PLURAL)) {
						if (thisPf.equals(pTgtText)) {
//							totCount++;
							matchedInsts.add(lingInst);
						}
					}
				}
			}

			this.lingThingPluralFormInsts.put(pTgtText, matchedInsts);

//			reportDebug("totCount=" + totCount, pAc);
		}

		return this.lingThingPluralFormInsts.get(pTgtText);
	}

}
