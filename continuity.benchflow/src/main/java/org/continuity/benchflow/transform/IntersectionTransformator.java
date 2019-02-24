package org.continuity.benchflow.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.continuity.api.entities.artifact.BehaviorModel;
import org.continuity.api.entities.artifact.BehaviorModel.Behavior;
import org.continuity.api.entities.artifact.BehaviorModel.MarkovState;
import org.continuity.api.entities.artifact.BehaviorModel.Transition;
import org.continuity.api.entities.report.AnnotationValidityReport;
import org.continuity.api.entities.report.AnnotationViolation;
import org.continuity.api.entities.report.AnnotationViolationType;
import org.continuity.api.entities.report.ApplicationChange;
import org.continuity.api.entities.report.ApplicationChangeReport;
import org.continuity.api.entities.report.ApplicationChangeType;
import org.continuity.api.entities.report.ModelElementReference;
import org.continuity.benchflow.artifact.ContinuITyModel;
import org.continuity.idpa.annotation.ApplicationAnnotation;
import org.continuity.idpa.annotation.ExtractedInput;
import org.continuity.idpa.annotation.validation.AnnotationValidityChecker;
import org.continuity.idpa.application.Application;
import org.continuity.idpa.application.Endpoint;
import org.continuity.idpa.application.HttpParameter;
import org.continuity.idpa.application.changes.ApplicationChangeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import cloud.benchflow.dsl.definition.workload.HttpWorkload;

/**
 * Provides operations for the intersection approach.
 * 
 * @author Manuel Palenga
 *
 */
public class IntersectionTransformator {

	private static final Logger LOGGER = LoggerFactory.getLogger(IntersectionTransformator.class);
	
	private static final String INITIAL_STATE = "INITIAL";
	
	/**
	 * Transforms the ContinuITy models from the provided versions into an intersection BenchFlow {@link HttpWorkload}.
	 * 
	 * @param systemName
	 * 			The name of the system.
	 * @param versions
	 * 			The different versions of the system.
	 * @param restTemplate
	 * 			Rest template to call REST interfaces.
	 * @return The intersection BenchFlow HTTP workload.
	 */
	public HttpWorkload getIntersectionWorkloadFromVersions(String systemName, List<String> versions, RestTemplate restTemplate) {
		
		List<ContinuITyModel> intersectionModels = this.getContinuITyModels(systemName, versions, restTemplate);
		
		// Calculates the intersection of the application model
		Application intersectionApplicationModel = intersectionModels.get(0).getApplication();
		intersectionApplicationModel.setVersion(null);	
		for(int i = 1; i < intersectionModels.size(); i++) {
			intersectionApplicationModel = this.getIntersectionIdpaApplicationModel(intersectionApplicationModel, intersectionModels.get(i).getApplication());
		}
		
		// Adjust the annotation model based on the application model
		ApplicationAnnotation intersectionAnnotationModel = this.getMatchedIdpaAnnotationModel(intersectionApplicationModel, intersectionModels.get(0).getAnnotation());
		
		// Get all behavior models from the defined versions
		List<BehaviorModel> behaviorsModels = intersectionModels.stream().filter(i -> i.getBehaviorModel() != null).map(i -> i.getBehaviorModel()).collect(Collectors.toList());
		if(behaviorsModels.isEmpty()) {
			String exceptionMessage = String.format("At least von BehaviorModel is required!");
			LOGGER.error(exceptionMessage);
			throw new IllegalArgumentException(exceptionMessage);
		}
		
		// Extraction of available requests
		for(int i = 0; i < behaviorsModels.size(); i++) {
			behaviorsModels.set(i, this.getValidBehaviorModelFromApplicationModel(behaviorsModels.get(i), intersectionApplicationModel));
		}
		
		// Merges the behavior of all available models
		BehaviorModel intersectionBehaviorModel = behaviorsModels.get(0);
		for(int i = 1; i < behaviorsModels.size(); i++) {
			intersectionBehaviorModel = this.mergeBehaviorModels(intersectionBehaviorModel, behaviorsModels.get(i), (1/ (i + 1)));
		}
		
		ContinuITyModel intersectionContinuITyModel = new ContinuITyModel(intersectionBehaviorModel, intersectionApplicationModel, intersectionAnnotationModel);

		TransformationExecutor transformationExecutor = new TransformationExecutor();
		HttpWorkload intersectionWorkload = transformationExecutor.transformToBenchFlowWorkloadModel(intersectionContinuITyModel);
		return intersectionWorkload;
	}
	
	/**
	 * Retrieves for each version one ContinuITy model
	 * 
	 * @param systemName
	 * 			The name of the system.
	 * @param versions
	 * 			The different versions of the system.
	 * @param restTemplate
	 * 			Rest template to call REST interfaces.
	 * @return ContinuITy models.
	 */
	private List<ContinuITyModel> getContinuITyModels(String systemName, List<String> versions, RestTemplate restTemplate) {
		// 
		TransformationExecutor transformationExecutor = new TransformationExecutor();
		List<ContinuITyModel> intersectionModels = new ArrayList<ContinuITyModel>();
		for(String version : versions) {
			ContinuITyModel model = transformationExecutor.getContinuITyModel(systemName, version, restTemplate);
			if(model == null) {
				String exceptionMessage = String.format("Models for SUT name '%s' and version '%s' are not available!", systemName, version);
				LOGGER.error(exceptionMessage);
				throw new IllegalArgumentException(exceptionMessage);
			}
			intersectionModels.add(model);
		}
		
		return intersectionModels;
	}
	
	/**
	 * Returns the intersection of both provided application models.
	 * 
	 * @param application
	 * 			An IDPA application model.
	 * @param changedApplication
	 * 			An IDPA application model.
	 * @return An intersection application model.
	 */
	@SuppressWarnings("unchecked")
	public Application getIntersectionIdpaApplicationModel(Application application, Application changedApplication) {

		ApplicationChangeDetector checker = new ApplicationChangeDetector(application);
		checker.compareTo(changedApplication);
			
		ApplicationChangeReport report = checker.getReport();
		for(ApplicationChange change : report.getApplicationChanges()) {

			if(change.getType() == ApplicationChangeType.ENDPOINT_ADDED || 
					change.getType() == ApplicationChangeType.ENDPOINT_CHANGED || 
					change.getType() == ApplicationChangeType.ENDPOINT_REMOVED) {
				String id = change.getChangedElement().getId();
				boolean removeEndpoint = application.getEndpoints().removeIf(e -> e.getId().equals(id));
				if(removeEndpoint) {
					LOGGER.debug("Removed endpoint with id '{}'.", id);
				}
			} else if(change.getType() == ApplicationChangeType.PARAMETER_ADDED || 
					change.getType() == ApplicationChangeType.PARAMETER_CHANGED || 
					change.getType() == ApplicationChangeType.PARAMETER_REMOVED) {
				String id = change.getChangedElement().getId();

				for(Endpoint<HttpParameter> httpEndpoint : application.getEndpoints().stream()
						.map(e -> (Endpoint<HttpParameter>) e)
						.collect(Collectors.toList())) {

					Optional<HttpParameter> parameter = httpEndpoint.getParameters().stream().filter(e -> e.getId().equals(id)).findAny();
					if(parameter.isPresent()) {
						application.getEndpoints().remove(httpEndpoint);
						LOGGER.debug("Removed endpoint with id '{}'.", httpEndpoint.getId());
						break;
					}				
				}
			}
		}
		
		application.getEndpoints().forEach(e -> LOGGER.debug("Intersection contains {}.", e.getId()));
		
		return application;
	}
	
	/**
	 * Returns a valid annotation model which matches to the provided application model.
	 * 
	 * @param application
	 * 				An IDPA application model.
	 * @param annotation
	 * 				An IDPA annotation model.
	 * @return Annotation model based on the provided application model.
	 */
	public ApplicationAnnotation getMatchedIdpaAnnotationModel(Application application, ApplicationAnnotation annotation) {	

		AnnotationValidityReport annotationReport = checkValidity(application, annotation);
		
		if (annotationReport.isOk()) {
			LOGGER.debug("No violations found. Annotation model is valid.");
			return annotation;
		}
		
		LOGGER.debug("Adapt the IDPA annotation model caused by some violations!");
		
		for(Entry<ModelElementReference, Set<AnnotationViolation>> entry : annotationReport.getViolations().entrySet()) {
			for(AnnotationViolation violation : entry.getValue()) {
				if(violation.getType() == AnnotationViolationType.ILLEGAL_ENDPOINT_REFERENCE) {
					String id = violation.getAffectedElement().getId();
					boolean removeEndpointAnnotation = annotation.getEndpointAnnotations().removeIf(a -> a.getAnnotatedEndpoint().getId().equals(id));

					if(!removeEndpointAnnotation) {
						continue;
					}
						
					// Gets all Regex extractions from annotation model
					for(ExtractedInput input : annotation.getInputs().stream()
							.filter(i -> i instanceof ExtractedInput)
							.map(i -> (ExtractedInput) i)
							.collect(Collectors.toList())) {
						boolean removeRegex = input.getExtractions().removeIf(e -> e.getFrom().getId().equals(id));
						if(removeRegex) {
							LOGGER.debug("Removed Regex extraction with '{}' from input '{}'.", id, input.getId());										
						}
					}
				}
			}
		}
		
		AnnotationValidityReport annotationReportCheck = checkValidity(application, annotation);
		
		if(!annotationReportCheck.isOk()) {
			LOGGER.warn("Something went wrong during calculation of IDPA models! Annotation violations: {}", annotationReportCheck);
			throw new IllegalArgumentException("Something went wrong during calculation of IDPA models! Annotation violations: "+ annotationReportCheck);			
		}
		
		return annotation;
	}
	
	
	private AnnotationValidityReport checkValidity(Application application, ApplicationAnnotation annotation) {
		AnnotationValidityChecker annotationChecker = new AnnotationValidityChecker(application);
		annotationChecker.checkAnnotation(annotation);
		return annotationChecker.getReport();
	}
	
	/**
	 * Returns the {@link BehaviorModel} which matches to the provided {@link Application}.
	 * In the returned behavior model are only states available which are included in the provided application model and
	 * the behavior is adapted according to the new application model.
	 * 
	 * @param behavior
	 * 			The {@link BehaviorModel} which should be adapted.
	 * @param application
	 * 			The corresponding application model of the behavior.
	 * @return A {@link BehaviorModel} which matches to the provided {@link Application}.
	 */
	public BehaviorModel getValidBehaviorModelFromApplicationModel(BehaviorModel behavior, Application application) {
		List<String> endpointIds = new ArrayList<String>();
		application.getEndpoints().forEach(e -> endpointIds.add(e.getId()));
		
		for(Behavior currentBehavior : behavior.getBehaviors()) {
			
			Map<String, MarkovState> mapMarkovStates = new HashMap<String, MarkovState>();
			currentBehavior.getMarkovStates().forEach(state -> mapMarkovStates.put(state.getId(), state));
			
			/*
			 * Check if the initial state of the behavior is removed, if yes adjust the transitions if possible
			 */
			boolean initialStateExists = endpointIds.contains(currentBehavior.getInitialState());
			if(!currentBehavior.getInitialState().equals(INITIAL_STATE) && !initialStateExists) {
				LOGGER.debug("Initial state '{}' was removed from the behavior '{}'.", currentBehavior.getInitialState(), currentBehavior.getName());
				
				Optional<MarkovState> optInitialState = currentBehavior.getMarkovStates().stream()
						.filter(s -> s.getId().equals(currentBehavior.getInitialState()))
						.findAny();
				if(!optInitialState.isPresent()) {
					String exceptionMessage = String.format("Initial state '%s' is not available in the behavior!", currentBehavior.getInitialState());
					LOGGER.error(exceptionMessage);
					throw new IllegalArgumentException(exceptionMessage);
				}
				
				List<Transition> transitions = optInitialState.get().getTransitions();
				if(transitions == null || transitions.isEmpty()) {
					currentBehavior.setInitialState(null);
					LOGGER.warn("Initial state which is not available in the application model, does not contain a transition!");
					continue;
				}
				this.adaptTransitions(currentBehavior, optInitialState.get());

				LOGGER.debug("Original initial state '{}' was renamed to 'INITIAL'.", optInitialState.get().getId());
				currentBehavior.setInitialState(INITIAL_STATE);
				optInitialState.get().setId(INITIAL_STATE);			
			}
			
			/*
			 * Remove all behavior states which are not in the application model and adjust the transitions
			 */
			List<MarkovState> notAvailableMarkovStates = currentBehavior.getMarkovStates().stream()
					.filter(state -> !state.getId().equals(INITIAL_STATE) && !endpointIds.contains(state.getId()))
					.collect(Collectors.toList());
			for(MarkovState state : notAvailableMarkovStates) {
				LOGGER.debug("Removed Markov state {}.", state.getId());
				currentBehavior.getMarkovStates().remove(state);
				this.adaptTransitions(currentBehavior, state);
			}
		}
		return behavior;
	}
	
	/**
	 * Removes all transitions from the behavior with the target state of the provided Markov state and 
	 * adds all transitions which are included in the provided Markov state to all Markov states which
	 * contains a transition to the provided Markov state. 
	 * 
	 * @param behavior
	 * 			All Markov states in this behavior are checked.
	 * @param state
	 * 			Markov state which should be deleted from the transitions.
	 */
	private void adaptTransitions(Behavior behavior, MarkovState removedState) {

		List<MarkovState> markovStatesWithTransitions = behavior.getMarkovStates().stream()
				.filter(s -> s.getTransitions() != null)
				.collect(Collectors.toList());
		
		// Nothing to adapt
		if(markovStatesWithTransitions.isEmpty()) {
			return;
		}
		
		List<Transition> removedStateTransitions = null;
		if(removedState.getTransitions() != null) {
		
			// Find transition which targets the removed state as loop.
			Optional<Transition> loopTransition = removedState.getTransitions().stream()
					.filter(t -> t.getTargetState().equals(removedState.getId()))
					.findAny();
			
			// All transitions which targets the removed state are not considered.
			removedStateTransitions = removedState.getTransitions().stream()
					.filter(t -> !t.getTargetState().equals(removedState.getId()))
					.collect(Collectors.toList());
			
			if(loopTransition.isPresent()) {
				double loopProbability = loopTransition.get().getProbability();
				removedStateTransitions.forEach(t -> t.setProbability(t.getProbability() * (1.0 / (1 - loopProbability))));
			}		
		} else {
			// No existing transitions from the removed state
			removedStateTransitions = new ArrayList<Transition>();
		}
		
		for(MarkovState otherState : markovStatesWithTransitions) {			
			for(int i = 0; i < otherState.getTransitions().size(); i++) {
				Transition transition = otherState.getTransitions().get(i);
				
				// Only transitions which targets the removed state
				if(!transition.getTargetState().equals(removedState.getId())) {
					continue;
				}
				
				otherState.getTransitions().remove(i);
				i--;
				
				// Adds all transitions of the removed target state to the actual state which invokes it
				for(Transition removedTransition : removedStateTransitions) {

					Optional<Transition> sameTargetTransition = otherState.getTransitions().stream()
							.filter(t -> t.getTargetState().equals(removedTransition.getTargetState()))
							.findFirst();
					
					// If a transition to the same target state exists then only the probability has to be adapted
					if(sameTargetTransition.isPresent()) {
						double bridgeProbability = transition.getProbability() * removedTransition.getProbability();
						sameTargetTransition.get().setProbability(sameTargetTransition.get().getProbability() + bridgeProbability);
						continue;
					}
					
					// Adds the neighbor transition to the state with a not existing transition target state
					Transition bridgeTransition = new Transition();
					bridgeTransition.setProbability(transition.getProbability() * removedTransition.getProbability());
					bridgeTransition.setTargetState(removedTransition.getTargetState());
					bridgeTransition.setMean(removedTransition.getMean());
					bridgeTransition.setDeviation(removedTransition.getDeviation());
					otherState.getTransitions().add(bridgeTransition);
				}
			}
		}
	}
	
	/**
	 * Merges both provided {@link BehaviorModel}.
	 * 
	 * @param behavior1
	 * 				First behavior model which should be merged.
	 * @param behavior2
	 * 				Second behavior model which should be merged.
	 * @return The merged behavior model.
	 */
	public BehaviorModel mergeBehaviorModels(BehaviorModel behavior1, BehaviorModel behavior2) {
		return this.mergeBehaviorModels(behavior1, behavior2, 0.5);
	}
	
	/**
	 * Merges both provided {@link BehaviorModel} with a provided probability weight factor of the provided behavior2.
	 * Example: As standard, if both behavior models contain one behavior with the probability of 1.0, then the resulting behavior model 
	 * will contain two behaviors each with the probability of 0.5. Using the probability weight factor with a value 0.3, the resulting
	 * behavior model will contain the first behavior with a probability of 0.7 and the second of 0.3.
	 * 
	 * @param behavior1
	 * 				First behavior model which should be merged.
	 * @param behavior2
	 * 				Second behavior model which should be merged.
	 * @param probabilityWeightFactor
	 * 				Probability weight of the behaviors from the provided behavior2. 
	 * 				Factor has to be in range [0,1] otherwise it is 0.5.
	 * @return
	 */
	public BehaviorModel mergeBehaviorModels(BehaviorModel behavior1, BehaviorModel behavior2, double probabilityWeightFactor) {
		
		if(probabilityWeightFactor < 0 || probabilityWeightFactor > 1) {
			probabilityWeightFactor = 0.5;
		}
		
		BehaviorModel mergedBehaviorModel = new BehaviorModel();
		List<Behavior> behaviors = new ArrayList<Behavior>();
		mergedBehaviorModel.setBehaviors(behaviors);
		
		if(behavior1 == behavior2) {
			for(Behavior behavior : behavior1.getBehaviors()) {
				behaviors.add(behavior.clone());
			}
			return mergedBehaviorModel;
		}
		
		int prefixIndexBehavior1 = getPrefixIndex(behavior1); 
        
		for(Behavior behavior : behavior1.getBehaviors()) {
			Behavior tempBehavior = behavior.clone();
			if(getIndex(tempBehavior.getName()) == null) {
				tempBehavior.setName("_" + prefixIndexBehavior1 + "_" + tempBehavior.getName());
			}		
			tempBehavior.setProbability(tempBehavior.getProbability() * (1 - probabilityWeightFactor));
			behaviors.add(tempBehavior);
		}
		for(Behavior behavior : behavior2.getBehaviors()) {
			Behavior tempBehavior = behavior.clone();
			Integer currentIndex = getIndex(tempBehavior.getName());
			if(currentIndex == null) {
				tempBehavior.setName("_" + (prefixIndexBehavior1 + 1) + "_" + tempBehavior.getName());
			} else {
				tempBehavior.setName(tempBehavior.getName().replaceAll("^_[0-9]+_", "_" + (prefixIndexBehavior1 + 1 + currentIndex) + "_"));
			}
			tempBehavior.setProbability(tempBehavior.getProbability() * (probabilityWeightFactor));
			behaviors.add(tempBehavior);
		}
	
		return mergedBehaviorModel;
	}
	
	private Integer getIndex(String name) {
		Pattern replace = Pattern.compile("^_[0-9]+_");
        Matcher matcher = replace.matcher(name);
        if(matcher.find()) {
        	String indexPrefix = matcher.group();
        	return Integer.parseInt(indexPrefix.substring(1, indexPrefix.length() - 1));
        } else {
        	return null;
        }
	}
	
	private int getPrefixIndex(BehaviorModel behaviorModel) {
		int highestPrefixIndex = 1;
        for(Behavior behavior : behaviorModel.getBehaviors()) {
        	Integer index = getIndex(behavior.getName());
            if(index != null && index >= highestPrefixIndex) {
            	highestPrefixIndex = index + 1;
            }
		}
        return highestPrefixIndex;
	}
}
