package com.ibm.ets.ita.ce.store.conversation.trigger.nl;

import static com.ibm.ets.ita.ce.store.utilities.FileUtilities.appendToSb;
import static com.ibm.ets.ita.ce.store.utilities.FileUtilities.appendToSbNoNl;

import java.util.ArrayList;

import com.ibm.ets.ita.ce.store.ActionContext;
import com.ibm.ets.ita.ce.store.conversation.model.ConvSentence;
import com.ibm.ets.ita.ce.store.conversation.model.ConvText;
import com.ibm.ets.ita.ce.store.conversation.model.ExtractedItem;
import com.ibm.ets.ita.ce.store.conversation.model.FinalItem;
import com.ibm.ets.ita.ce.store.conversation.model.NewMatchedTriple;
import com.ibm.ets.ita.ce.store.conversation.model.ProcessedWord;
import com.ibm.ets.ita.ce.store.conversation.trigger.general.Card;
import com.ibm.ets.ita.ce.store.conversation.trigger.general.CardGenerator;
import com.ibm.ets.ita.ce.store.conversation.trigger.general.Concept;
import com.ibm.ets.ita.ce.store.conversation.trigger.general.ConvCeGenerator;
import com.ibm.ets.ita.ce.store.conversation.trigger.general.GeneralProcessor;
import com.ibm.ets.ita.ce.store.conversation.trigger.general.Property;
import com.ibm.ets.ita.ce.store.conversation.trigger.general.Reply;
import com.ibm.ets.ita.ce.store.handler.QueryHandler;
import com.ibm.ets.ita.ce.store.model.CeConcept;
import com.ibm.ets.ita.ce.store.model.CeInstance;
import com.ibm.ets.ita.ce.store.model.CeProperty;
import com.ibm.ets.ita.ce.store.model.CePropertyInstance;
import com.ibm.ets.ita.ce.store.model.container.ContainerCeResult;

public class NlProcessor extends GeneralProcessor {

    private ConvText convText;
    private NlTriggerHandler th;
    private NlAnswerGenerator ag;
    private NlSentenceProcessor sp;
    private NlQuestionProcessor qp;

    public NlProcessor(ActionContext ac, NlTriggerHandler th) {
        this.ac = ac;
        this.th = th;
        cg = new CardGenerator(ac);
        ag = new NlAnswerGenerator(ac);
        sp = new NlSentenceProcessor(ac);
        qp = new NlQuestionProcessor();
    }

    // Process card
    public void process(CeInstance cardInst) {
        String nlText = cardInst.getSingleValueFromPropertyNamed(Property.CONTENT.toString());
        String modNlText = appendDotIfNeeded(nlText);
        System.out.println("text: " + modNlText);

        // Test for valid CE
        if (isValidCe(modNlText)) {
            // Valid CE
            cg.generateTellCard(cardInst, modNlText, th.getTriggerName(), th.getTellServiceName());
        } else {
            // NL
            convText = ConvText.createNewText(ac, nlText);

            ArrayList<CeInstance> matchingAgents = new ArrayList<CeInstance>();
            ArrayList<CeInstance> matchingKeywords = new ArrayList<CeInstance>();

            for (ConvSentence sentence : convText.getChildSentences()) {
                // Process sentence against bag of words
                ArrayList<ProcessedWord> words = sp.process(sentence, cardInst);
                sp.extractMatchingEntities(sentence, words);

                // Find agents matching keywords as defined in config
                findMatchingAgents(sentence.getSentenceText(), matchingAgents, matchingKeywords);
                System.out.println("Matching agents: " + matchingAgents);

                if (matchingAgents.size() > 1) {
                    // Confirm which agent with user
                    askToConfirmAgent(cardInst, matchingAgents);
                } else if (matchingAgents.size() == 1) {
                    // Run agent
                    CeInstance agent = matchingAgents.get(0);
                    sendToAgent(agent, words, matchingKeywords, cardInst, nlText);
                } else {
                    // No matching agents, find command words with matching
                    // templates
                    ArrayList<CeInstance> matchingCommands = findMatchingCommands(words);

                    if (!matchingCommands.isEmpty()) {
                        // Matching commands
                        if (matchingCommands.size() > 1) {
                            // TODO: Confirm which command with user

                        } else {
                            // Execute command query
                            CeInstance command = matchingCommands.get(0);
                            executeCommand(command, words, cardInst);
                        }
                    } else {
                        // No matching commands
                        if (fromTellService(cardInst)) {
                            // Reply from Tell
                            forwardTellResponse(cardInst, nlText);
                        } else if (isInterestingQuestion(nlText)) {
                            // TODO: Ignore interesting question?
                        } else if (isInReplyToConfirm(cardInst) && isConfirmResponse(nlText)) {
                            processConfirmResponse(cardInst, nlText);
                        } else if (convText.isQuestion()) {
                            // Respond to NL question - eventually nothing
                            // should reach this point if all question words are
                            // rewritten as templated command words
                            ArrayList<FinalItem> finalItems = qp.getFinalItems(words);
                            ArrayList<FinalItem> optionItems = qp.getOptionItems(words);
                            ArrayList<FinalItem> maybeItems = qp.getMaybeItems(words);

                            replyToNLQuestion(cardInst, finalItems, optionItems, maybeItems);
                        } else {
                            // Other NL - attempted fact sentence
                            // TODO: Move code into NlFactProcessor
                            ArrayList<NewMatchedTriple> matchedTriples = sp.matchTriples(words, ag);

                            replyToNlFact(cardInst, matchedTriples);
                        }
                    }
                }
            }
        }
    }

    // Multiple agents have matched on sentence. Ask user to be more specific
    private void askToConfirmAgent(CeInstance cardInst, ArrayList<CeInstance> matchingAgents) {
        StringBuilder sb = new StringBuilder();
        appendToSbNoNl(sb, Reply.STATEMENT_MATCHES_MULTIPLE.toString());

        for (int i = 0; i < matchingAgents.size(); ++i) {
            CeInstance agent = matchingAgents.get(i);

            appendToSbNoNl(sb, agent.getInstanceName());

            if (i < matchingAgents.size() - 2) {
                appendToSbNoNl(sb, ",");
            } else if (i < matchingAgents.size() - 1) {
                appendToSbNoNl(sb, " and ");
            }
        }

        appendToSb(sb, Reply.AGENTS.toString());

        appendToSbNoNl(sb, Reply.BE_SPECIFIC.toString());
        String humanAgent = findHumanAgent(cardInst);
        cg.generateCard(Card.NL.toString(), sb.toString(), th.getTriggerName(), humanAgent, cardInst.getInstanceName(),
                null);
    }

    // Matched on one agent. Use template to do agent processing
    private void sendToAgent(CeInstance agent, ArrayList<ProcessedWord> words, ArrayList<CeInstance> matchingKeywords,
            CeInstance cardInst, String text) {
        ArrayList<CeInstance> templates = agent.getInstanceListFromPropertyNamed(ac, Property.TEMPLATE.toString());

        for (CeInstance template : templates) {
            boolean thingsFound = true;
            CeInstance matchingInstance = null;
            CeConcept matchingConcept = null;

            if (template.isConceptNamed(ac, Concept.INSTANCE_TEMPLATE.toString())) {
                for (ProcessedWord word : words) {
                    CeInstance extractedInstance = sp.getMatchingInstance(word);

                    if (extractedInstance != null && !matchingKeywords.contains(extractedInstance)) {
                        matchingInstance = extractedInstance;
                    }
                }

                thingsFound = thingsFound && (matchingInstance != null);
            } else if (template.isConceptNamed(ac, Concept.CONCEPT_TEMPLATE.toString())) {
                for (ProcessedWord word : words) {
                    matchingConcept = sp.getMatchingConcept(word);
                }

                thingsFound = thingsFound && (matchingConcept != null);
            }

            if (thingsFound) {
                String templateString = template.getLatestValueFromPropertyNamed(Property.TEMPLATE_STRING.toString());
                String recipient = template.getLatestValueFromPropertyNamed(Property.RECIPIENT.toString());
                String reply = template.getLatestValueFromPropertyNamed(Property.REPLY.toString());
                String interestedUser = cardInst.getSingleValueFromPropertyNamed(Property.IS_FROM.toString());

                String completedRecipient = substituteItemsIntoTemplate(recipient, null, matchingInstance,
                        matchingConcept, interestedUser, text);
                String completedTemplate = substituteItemsIntoTemplate(templateString, null, matchingInstance,
                        matchingConcept, interestedUser, text);
                String completedReply = substituteItemsIntoTemplate(reply, null, matchingInstance, matchingConcept,
                        interestedUser, text);

                cg.generateCard(Card.TELL.toString(), completedTemplate, th.getTriggerName(), completedRecipient,
                        cardInst.getInstanceName(), null);
                cg.generateCard(Card.NL.toString(), completedReply, th.getTriggerName(), interestedUser,
                        cardInst.getInstanceName(), null);
            }
        }
    }

    // Pass on Tell agent's message to human agent if other agent reply not
    // already sent
    private void forwardTellResponse(CeInstance cardInst, String convText) {
        if (!templateAgentAlreadySentReply(cardInst) && convText.equals(Reply.SAVED.message())) {
            String humanAgent = findHumanAgent(cardInst);
            cg.generateNLCard(cardInst, convText, th.getTriggerName(), humanAgent, null);
        }
    }

    // Process either a yes or no response to confirm card
    private void processConfirmResponse(CeInstance cardInst, String text) {
        ArrayList<CeInstance> possibleReplies = ac.getModelBuilder().getAllInstancesForConceptNamed(ac,
                Concept.POSITIVE_CONFIRM_REPLY.toString());
        boolean positiveResponse = false;

        for (CeInstance reply : possibleReplies) {
            ArrayList<String> words = reply.getValueListFromPropertyNamed(Property.WORD.toString());

            for (String word : words) {
                String regex = "(?s).*\\b" + word.toLowerCase() + "\\b.*";
                positiveResponse = positiveResponse || text.toLowerCase().matches(regex);
            }
        }

        if (positiveResponse) {
            // Positive response
            forwardConfirmResponse(cardInst);
        } else {
            // Negative response
            String humanAgent = findHumanAgent(cardInst);
            cg.generateCard(Card.NL.toString(), Reply.NOT_SAVED.toString(), th.getTriggerName(), humanAgent,
                    cardInst.getInstanceName(), null);
        }
    }

    // Pass on the CE the Human agent confirmed to Tell
    private void forwardConfirmResponse(CeInstance cardInst) {
        CeInstance confirmCard = cardInst.getSingleInstanceFromPropertyNamed(ac, Property.IN_REPLY_TO.toString());

        if (confirmCard.isConceptNamed(ac, Card.CONFIRM.toString())) {
            String content = confirmCard.getSingleValueFromPropertyNamed(Property.CONTENT.toString());
            cg.generateCard(Card.TELL.toString(), content, th.getTriggerName(), th.getTellServiceName(),
                    cardInst.getInstanceName(), null);
        }
    }

    // NL Question found. Try and reply to matched instances, concepts and
    // properties.
    private void replyToNLQuestion(CeInstance cardInst, ArrayList<FinalItem> finalItems,
            ArrayList<FinalItem> optionItems, ArrayList<FinalItem> maybeItems) {
        // TODO: Convert NL questions into CE queries and pass to Ask agent
        StringBuilder sb = new StringBuilder();

        if (optionItems != null && !optionItems.isEmpty()) {
            // Options are available so reply asking for clarification
            sb.append(ag.answerOptionQuestion(optionItems));
        } else if (maybeItems != null && !maybeItems.isEmpty()) {
            // Potential matching items available
            sb.append(ag.answerMaybeQuestion(maybeItems));
        } else if (finalItems != null && !finalItems.isEmpty()) {
            // Only final items available so answer with information about them
            // TODO: Compute who/what/where answers differently
            sb.append(ag.answerStandardQuestion(finalItems));
        }

        // If string builder is empty, then nothing has been understood
        if (sb.toString().isEmpty()) {
            sb.append(ag.nothingUnderstood());
        }

        ArrayList<String> referencedItems = new ArrayList<String>();
        ArrayList<CeInstance> referencedInsts = new ArrayList<CeInstance>();

        extractReferencedItems(finalItems, referencedItems, referencedInsts);

        // Generate NL Card with reply
        String humanAgent = findHumanAgent(cardInst);
        cg.generateNLCard(cardInst, sb.toString(), th.getTriggerName(), humanAgent, referencedItems);
    }

    private void replyToNlFact(CeInstance cardInst, ArrayList<NewMatchedTriple> triples) {
        StringBuilder sb = new StringBuilder();
        String cardType = Card.NL.toString();
        ArrayList<String> referencedInsts = null;

        System.out.println("\nSize: " + triples.size());
        System.out.println(triples);

        if (triples.size() == 1) {
            NewMatchedTriple triple = triples.get(0);

            ConvCeGenerator ccg = new ConvCeGenerator(ac);
            sb.append(ccg.generateFactFromTriple(triple));

            cardType = Card.CONFIRM.toString();
            referencedInsts = triple.getReferencedInstances();
        }

        // If string builder is empty, then nothing has been understood
        if (sb.toString().isEmpty()) {
            sb.append(ag.nothingUnderstood());
        }

        // Generate NL Card with reply
        String humanAgent = findHumanAgent(cardInst);
        cg.generateCard(cardType, sb.toString(), th.getTriggerName(), humanAgent, cardInst.getInstanceName(),
                referencedInsts);
    }

    // Find agents with matching keywords
    private void findMatchingAgents(String sentence, ArrayList<CeInstance> matchingAgents,
            ArrayList<CeInstance> matchingKeywords) {
        ArrayList<CeInstance> agents = ac.getModelBuilder().getAllInstancesForConceptNamed(ac,
                Concept.AGENT.toString());

        for (CeInstance agent : agents) {
            ArrayList<CeInstance> keywords = agent.getInstanceListFromPropertyNamed(ac, Property.KEYWORD.toString());

            for (CeInstance keyword : keywords) {
                String regex = "(?s).*\\b" + keyword.getInstanceName().toLowerCase() + "\\b.*";
                // TODO: Do this using final items
                if (sentence.toLowerCase().matches(regex)) {
                    matchingAgents.add(agent);
                    matchingKeywords.add(keyword);
                }
            }
        }
    }

    private boolean matchesAgents(String sentence) {
        ArrayList<CeInstance> agents = ac.getModelBuilder().getAllInstancesForConceptNamed(ac,
                Concept.AGENT.toString());

        for (CeInstance agent : agents) {
            ArrayList<CeInstance> keywords = agent.getInstanceListFromPropertyNamed(ac, Property.KEYWORD.toString());

            for (CeInstance keyword : keywords) {
                // TODO: Do this using final items
                String regex = "(?s).*\\b" + keyword.getInstanceName().toLowerCase() + "\\b.*";
                if (sentence.toLowerCase().matches(regex)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Substitute mentioned instances, concepts, from user and original text
    // into agent template
    private String substituteItemsIntoTemplate(String str, CeProperty property, CeInstance instance, CeConcept concept,
            String user, String originalText) {
        if (property != null) {
            str = str.replace("~ P ~", property.getPropertyName());
        }
        if (instance != null) {
            str = str.replace("~ I ~", instance.getInstanceName());
        }
        if (concept != null) {
            str = str.replace("~ C ~", concept.getConceptName());
        }
        if (user != null) {
            str = str.replace("~ U ~", user);
        }
        if (originalText != null) {
            str = str.replace("~ S ~", originalText);
        }
        if (originalText != null) {
            str = str.replace("~ UID ~", "{uid}");
        }
        if (originalText != null) {
            str = str.replace("~ NOW ~", "{now}");
        }

        return str;
    }

    // Extract referenced items to set 'about' property in card
    private void extractReferencedItems(ArrayList<FinalItem> allFinalItems, ArrayList<String> referencedItems,
            ArrayList<CeInstance> referencedInsts) {
        for (FinalItem item : allFinalItems) {
            ArrayList<ExtractedItem> extractedItems = item.getExtractedItems();

            if (item.isPropertyInstanceItem()) {
                CeInstance instance = null;
                CeProperty property = null;
                ArrayList<CeProperty> properties = null;

                // Extract instance and property
                for (ExtractedItem extractedItem : extractedItems) {
                    if (extractedItem.isInstanceItem()) {
                        instance = extractedItem.getInstance();
                        referencedItems.add(instance.getInstanceName());
                    } else if (extractedItem.isPropertyItem()) {
                        properties = extractedItem.getPropertyList();
                    }
                }

                CeConcept[] instanceConcepts = instance.getDirectConcepts();

                // Find property that matches instance
                for (CeProperty prop : properties) {
                    CeConcept propertyConcept = prop.getDomainConcept();

                    for (CeConcept instanceConcept : instanceConcepts) {
                        if (instanceConcept.equals(propertyConcept)) {
                            property = prop;
                            break;
                        }
                    }
                }

                // Add value from property instance
                CePropertyInstance propertyInstance = instance.getPropertyInstanceForProperty(property);

                if (propertyInstance != null) {
                    String value = propertyInstance.getFirstPropertyValue().getValue();
                    referencedItems.add(value);
                }
            } else {
                for (ExtractedItem extractedItem : extractedItems) {
                    if (extractedItem.isInstanceItem()) {
                        referencedItems.add(extractedItem.getInstance().getInstanceName());
                    } else {
                        // TODO: do something with concepts and properties
                    }
                }
            }
        }
    }

    private CeInstance getLastHumanAgent(CeInstance cardInst) {
        CeInstance prevCard = cardInst;

        while (fromTellService(prevCard) || fromNLService(prevCard)) {
            if (prevCard == null) {
                return null;
            }

            prevCard = prevCard.getSingleInstanceFromPropertyNamed(ac, Property.IN_REPLY_TO.toString());
        }

        return prevCard;
    }

    // Find the last spoke to human agent from earlier in the conversation
    private String findHumanAgent(CeInstance cardInst) {
        CeInstance prevCard = getLastHumanAgent(cardInst);

        if (prevCard != null) {
            String humanAgent = prevCard.getSingleValueFromPropertyNamed(Property.IS_FROM.toString());
            return humanAgent;
        } else {
            return null;
        }
    }

    // Execute command based on command word and matching template
    private void executeCommand(CeInstance command, ArrayList<ProcessedWord> words, CeInstance cardInst) {
        ArrayList<CeInstance> templates = command.getInstanceListFromPropertyNamed(ac, Property.TEMPLATE.toString());

        CeProperty matchingProperty = null;
        CeInstance matchingInstance = null;
        CeConcept matchingConcept = null;

        String completeQuery = null;
        String completeReply = null;

        // Get query, reply and matching items from template
        for (CeInstance template : templates) {
            boolean matchedTemplate = false;

            String query = template.getSingleValueFromPropertyNamed(Property.TEMPLATE_STRING.toString());
            String reply = template.getSingleValueFromPropertyNamed(Property.REPLY.toString());

            if (template.isConceptNamed(ac, Concept.PROPERTY_TEMPLATE.toString())) {
                // Template requires property
                for (ProcessedWord word : words) {
                    if (!word.equals(command.getInstanceName()) && word.isGroundedOnProperty()
                            && matchingProperty == null) {
                        matchingProperty = sp.getMatchingProperty(word, ag);
                        matchedTemplate = true;
                        System.out.println("Matching property: " + matchingProperty);
                    }
                }
            } else if (template.isConceptNamed(ac, Concept.INSTANCE_TEMPLATE.toString())) {
                // Template requires instance
                for (ProcessedWord word : words) {
                    CeInstance wordInstance = sp.getMatchingInstance(word);

                    if (wordInstance != command && matchingInstance == null) {
                        matchingInstance = wordInstance;
                        matchedTemplate = true;
                    }
                }
            } else if (template.isConceptNamed(ac, Concept.CONCEPT_TEMPLATE.toString())) {
                // Template requires concept
                for (ProcessedWord word : words) {
                    if (!word.equals(command.getInstanceName()) && word.isGroundedOnConcept()
                            && matchingConcept == null) {
                        matchingConcept = sp.getMatchingConcept(word);
                        matchedTemplate = true;
                        System.out.println("Matching concept: " + matchingConcept);
                    }
                }
            } else {
                // Template has no requirements
                matchedTemplate = true;
            }

            if (matchedTemplate) {
                completeQuery = substituteItemsIntoTemplate(query, matchingProperty, matchingInstance, matchingConcept,
                        null, null);
                completeReply = substituteItemsIntoTemplate(reply, matchingProperty, matchingInstance, matchingConcept,
                        null, null);

                if (matchingProperty != null) {
                    completeQuery = completeQuery.replace("~ C1 ~",
                            matchingProperty.getDomainConcept().getConceptName());
                    completeQuery = completeQuery.replace("~ C2 ~",
                            matchingProperty.getRangeConcept().getConceptName());
                }

                System.out.println("Completed query:");
                System.out.println(completeQuery);
            }
        }

        if (completeQuery != null) {
            // Execute query
            QueryHandler qh = new QueryHandler(this.ac);
            ContainerCeResult result = qh.executeUserSpecifiedCeQuery(completeQuery, null, null);

            completeReply = completeReply.replace("~ N ~", new Integer(result.getResultRows().size()).toString());

            // Do substitutions for CE
            ArrayList<String> ceResults = result.getCeResults();
            StringBuilder sb = new StringBuilder();

            for (String ce : ceResults) {
                appendToSb(sb, ce);
            }

            completeReply = completeReply.replace("~ CE ~", sb.toString());

            // Do substitutions for headers
            ArrayList<String> headers = result.getHeaders();
            String[] splits = completeReply.split("~");
            sb = new StringBuilder();

            for (String split : splits) {
                String trimmedSplit = split.trim();

                for (String header : headers) {
                    if (trimmedSplit.equals(header)) {
                        int index = result.getIndexForHeader(header);

                        StringBuilder replacement = new StringBuilder();

                        ArrayList<ArrayList<String>> resultRows = result.getResultRows();
                        for (int i = 0; i < resultRows.size(); ++i) {
                            ArrayList<String> row = resultRows.get(i);
                            replacement.append(row.get(index));

                            if (i < resultRows.size() - 2) {
                                replacement.append(", ");
                            } else if (i < resultRows.size() - 1) {
                                replacement.append(" and ");
                            }
                        }

                        split = replacement.toString();
                    }
                }
                sb.append(split);
            }
            System.out.println(sb.toString());

            String humanAgent = findHumanAgent(cardInst);
            cg.generateCard(Card.NL.toString(), sb.toString(), th.getTriggerName(), humanAgent,
                    cardInst.getInstanceName(), null);
        }
    }

    // Loop through templates on mentioned command words and add required
    // matching items
    private ArrayList<CeInstance> findMatchingCommands(ArrayList<ProcessedWord> words) {
        ArrayList<CeInstance> instances = new ArrayList<CeInstance>();

        for (ProcessedWord word : words) {
            if (word.isGroundedOnInstance()) {
                CeInstance instance = sp.getMatchingInstance(word);

                if (instance != null) {
                    if (instance.isConceptNamed(ac, Concept.COMMAND_WORD.toString())) {
                        ArrayList<CeInstance> templates = instance.getInstanceListFromPropertyNamed(ac,
                                Property.TEMPLATE.toString());

                        for (CeInstance template : templates) {
                            if (template.isConceptNamed(ac, Concept.PROPERTY_TEMPLATE.toString())) {
                                // Template requires property
                                for (ProcessedWord matchingWord : words) {
                                    if (matchingWord != word && matchingWord.isGroundedOnProperty()
                                            && !instances.contains(instance)) {
                                        instances.add(instance);
                                    }
                                }
                            } else if (template.isConceptNamed(ac, Concept.INSTANCE_TEMPLATE.toString())) {
                                // Template requires instance
                                for (ProcessedWord matchingWord : words) {
                                    if (matchingWord != word && matchingWord.isGroundedOnInstance()
                                            && !instances.contains(instance)) {
                                        instances.add(instance);
                                    }
                                }
                            } else if (template.isConceptNamed(ac, Concept.CONCEPT_TEMPLATE.toString())) {
                                // Template requires concept
                                for (ProcessedWord matchingWord : words) {
                                    if (matchingWord != word && matchingWord.isGroundedOnConcept()
                                            && !instances.contains(instance)) {
                                        instances.add(instance);
                                    }
                                }
                            } else {
                                // Template has no requirements
                                if (!instances.contains(instance)) {
                                    instances.add(instance);
                                }
                            }
                        }
                    }
                }
            }
        }

        return instances;
    }

    private boolean templateAgentAlreadySentReply(CeInstance cardInst) {
        CeInstance lastHumanCard = getLastHumanAgent(cardInst);
        return matchesAgents(lastHumanCard.getSingleValueFromPropertyNamed(Property.CONTENT.toString()));
    }

    private boolean fromNLService(CeInstance cardInst) {
        String fromService = cardInst.getSingleValueFromPropertyNamed(Property.IS_FROM.toString());
        return fromService.equals(th.getTriggerName());
    }

    private boolean fromTellService(CeInstance cardInst) {
        String fromService = cardInst.getSingleValueFromPropertyNamed(Property.IS_FROM.toString());
        return fromService.equals(th.getTellServiceName());
    }

    private boolean isInterestingQuestion(String text) {
        return text.contains(Reply.NEW_INTERESTING.toString());
    }

    private boolean isInReplyToConfirm(CeInstance cardInst) {
        CeInstance inReplyTo = cardInst.getSingleInstanceFromPropertyNamed(ac, Property.IN_REPLY_TO.toString());

        if (inReplyTo != null) {
            return inReplyTo.isConceptNamed(ac, Card.CONFIRM.toString());
        } else {
            return false;
        }
    }

    private boolean isConfirmResponse(String text) {
        ArrayList<CeInstance> possibleReplies = ac.getModelBuilder().getAllInstancesForConceptNamed(ac,
                Concept.CONFIRM_REPLY.toString());
        boolean confirmResponse = false;

        for (CeInstance reply : possibleReplies) {
            ArrayList<String> words = reply.getValueListFromPropertyNamed(Property.WORD.toString());

            for (String word : words) {
                String regex = "(?s).*\\b" + word.toLowerCase() + "\\b.*";
                confirmResponse = confirmResponse || text.toLowerCase().matches(regex);
            }
        }

        return confirmResponse;
    }

    // Trim leading and trailing whitespace and append full stop if needed
    private String appendDotIfNeeded(String text) {
        String result = text.trim();

        if (!result.endsWith(".")) {
            result += ".";
        }

        return result;
    }
}
