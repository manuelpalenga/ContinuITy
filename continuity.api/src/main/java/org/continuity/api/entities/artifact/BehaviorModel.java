package org.continuity.api.entities.artifact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.continuity.api.entities.deserialization.BehaviorModelSerializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * 
 * @author Manuel Palenga
 *
 */
public class BehaviorModel {

	@JsonInclude(Include.NON_NULL)
	private List<Behavior> behaviors;

	public BehaviorModel() {

	}

	public BehaviorModel(List<String[][]> csvRepresentations) {
		super();
		List<Behavior> behaviors = new ArrayList<Behavior>();
		for (String[][] csvRepresentation : csvRepresentations) {
			behaviors.add(BehaviorModelSerializer.deserializeBehaviorModel(csvRepresentation));
		}
	}
	
	public void setBehaviors(List<Behavior> behaviors) {
		this.behaviors = behaviors;
	}

	public List<Behavior> getBehaviors() {
		return behaviors;
	}

	public static class Behavior {

		@JsonInclude(Include.NON_NULL)
		private String name;

		@JsonInclude(Include.NON_NULL)
		private String initialState;

		@JsonInclude(Include.NON_NULL)
		private Double probability;

		@JsonProperty(value = "markov-states")
		@JsonInclude(Include.NON_NULL)
		private List<MarkovState> markovStates;

		public Behavior() {
		}
		
		public Behavior(String name, String initialState, Double probability, List<MarkovState> markovStates) {
			this.name = name;
			this.initialState = initialState;
			this.probability = probability;
			this.markovStates = markovStates;
		}
		
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getInitialState() {
			return initialState;
		}

		public void setInitialState(String initialState) {
			this.initialState = initialState;
		}

		public Double getProbability() {
			return probability;
		}

		public void setProbability(Double probability) {
			this.probability = probability;
		}

		public List<MarkovState> getMarkovStates() {
			return markovStates;
		}

		public void setMarkovStates(List<MarkovState> markovStates) {
			this.markovStates = markovStates;
		}

		public MarkovState getMarkovState(String rootMarkovStateName) {
			for (MarkovState markovState : markovStates) {
				if (markovState.getId().equals(rootMarkovStateName)) {
					return markovState;
				}
			}
			return null;
		}
		
		@Override
		public Behavior clone() {
			Behavior behavior = null;
			try {
				behavior = (Behavior) super.clone();
			} catch (CloneNotSupportedException e) {
				behavior = new Behavior(this.name, this.initialState, this.probability, null);
			}
			if(this.markovStates == null) {
				return behavior;
			}
			behavior.setMarkovStates(new ArrayList<MarkovState>());
			for(MarkovState state : this.markovStates) {
				behavior.getMarkovStates().add(state.clone());
			}
			return behavior;
		}
	}

	public static class MarkovState {

		@JsonProperty(required = true)
		@JsonInclude(Include.NON_NULL)
		private String id;

		@JsonInclude(Include.NON_NULL)
		private List<Transition> transitions;

		public MarkovState() {
		}
		
		public MarkovState(String id, List<Transition> transitions) {
			this.id = id;
			this.transitions = transitions;
		}
		
		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public List<Transition> getTransitions() {
			return transitions;
		}

		public void setTransitions(List<Transition> transitions) {
			this.transitions = transitions;
		}

		public void addTransition(String target, double propability, double mean, double deviation) {
			if (null == transitions) {
				transitions = new ArrayList<Transition>(Arrays.asList(new Transition(target, propability, mean, deviation)));
			} else {
				transitions.add(new Transition(target, propability, mean, deviation));
			}
		}
		
		@Override
		public MarkovState clone() {
			MarkovState state = null;
			
			try {
				state = (MarkovState) super.clone();
			} catch (CloneNotSupportedException e) {
				state = new MarkovState(this.id, null);
			}
			if(this.transitions == null) {
				return state;
			}
			state.setTransitions(new ArrayList<Transition>());
			for(Transition transition : this.transitions) {
				state.getTransitions().add(transition.clone());
			}
			return state;
		}

	}

	public static class Transition {

		@JsonProperty(required = true)
		@JsonInclude(Include.NON_NULL)
		private String targetState;

		@JsonInclude(Include.NON_NULL)
		private Double probability;

		@JsonProperty(value = "think-time-mean")
		@JsonInclude(Include.NON_NULL)
		private Double mean;

		@JsonProperty(value = "think-time-deviation")
		@JsonInclude(Include.NON_NULL)
		private Double deviation;
		
		/**
		 * Default constructor.
		 */
		public Transition() {
		}

		/**
		 * Constructor
		 * 
		 * @param targetState
		 *            the target state
		 * @param propability
		 *            the propability
		 * @param mean
		 *            the mean of the thinktime
		 * @param deviation
		 *            the deviation of the thinktime
		 */
		public Transition(String targetState, Double propability, Double mean, Double deviation) {
			this.targetState = targetState;
			this.probability = propability;
			this.mean = mean;
			this.deviation = deviation;
		}

		public String getTargetState() {
			return targetState;
		}

		public void setTargetState(String targetState) {
			this.targetState = targetState;
		}

		public Double getProbability() {
			return probability;
		}

		public void setProbability(Double probability) {
			this.probability = probability;
		}

		public Double getMean() {
			return mean;
		}

		public void setMean(Double mean) {
			this.mean = mean;
		}

		public Double getDeviation() {
			return deviation;
		}

		public void setDeviation(Double deviation) {
			this.deviation = deviation;
		}
		
		@Override
		public Transition clone() {
			try {
				return (Transition) super.clone();
			} catch (CloneNotSupportedException e) {
				return new Transition(this.targetState, this.probability, this.mean, this.deviation);
			}
		}
	}
}
