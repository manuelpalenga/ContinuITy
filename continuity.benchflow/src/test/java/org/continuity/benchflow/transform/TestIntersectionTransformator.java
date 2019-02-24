package org.continuity.benchflow.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.continuity.api.entities.artifact.BehaviorModel;
import org.continuity.api.entities.artifact.BehaviorModel.Behavior;
import org.continuity.api.entities.artifact.BehaviorModel.MarkovState;
import org.continuity.api.entities.artifact.BehaviorModel.Transition;
import org.continuity.benchflow.BenchFlowTestHelper;
import org.continuity.benchflow.artifact.ContinuITyModel;
import org.continuity.idpa.annotation.ApplicationAnnotation;
import org.continuity.idpa.application.Application;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.beust.jcommander.internal.Lists;

import cloud.benchflow.dsl.definition.workload.HttpWorkload;
import cloud.benchflow.dsl.definition.workload.drivertype.DriverType;
import cloud.benchflow.dsl.definition.workload.operation.Operation;
import cloud.benchflow.dsl.definition.workload.workloaditem.HttpWorkloadItem;
import scala.collection.JavaConverters;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TransformationExecutor.class)
public class TestIntersectionTransformator {

	private IntersectionTransformator transformater = null;
	private BenchFlowTestHelper testHelper = null;
		
	@Before
	public void setUp() {
		transformater = new IntersectionTransformator();
		testHelper = new BenchFlowTestHelper();
	}
	
	private BehaviorModel.Transition createTransition(final double probability, final String targetState){
		BehaviorModel.Transition transition = new BehaviorModel.Transition();
		transition.setProbability(probability);
		transition.setTargetState(targetState);
		return transition;
	}
	
	private List<Transition> getTransitions(Transition... transitions){
		List<Transition> listTransitions = new ArrayList<Transition>();
		for(Transition transition : transitions) {
			listTransitions.add(transition);
		}
		return listTransitions;
	}
	
	private MarkovState createMarkovState(final String id){
		MarkovState state = new MarkovState();
		state.setId(id);
		return state;
	}
	
	private void checkTransitions(List<Transition> transitions, String[] targets, Double[] probabilities) {
		assertEquals(targets.length, transitions.size());
		for(int i = 0; i < targets.length; i++) {
			assertEquals(targets[i], transitions.get(i).getTargetState());
			assertEquals(probabilities[i], transitions.get(i).getProbability(), 0.000001);
		}
	}
	
	@Test
	public void testAdaptTransitionsWithOneResultTransition() throws Exception {
		Behavior behavior = new Behavior();
		behavior.setName("test_behavior");
		behavior.setProbability(1.0);		
		List<MarkovState> markovStates = new ArrayList<MarkovState>();
		behavior.setMarkovStates(markovStates);
		
		MarkovState state1 = this.createMarkovState("state_1");
		state1.setTransitions(this.getTransitions(this.createTransition(0.75, "state_2"), this.createTransition(0.25, "state_3")));
		markovStates.add(state1);
		
		MarkovState removedState = this.createMarkovState("state_2");
		removedState.setTransitions(this.getTransitions(this.createTransition(1.0, "state_3")));
		
		MarkovState state3 = this.createMarkovState("state_3");
		markovStates.add(state3);
		
		Whitebox.invokeMethod(transformater, "adaptTransitions", behavior, removedState);
		
		assertEquals(2, behavior.getMarkovStates().size());
		
		assertEquals("state_1", behavior.getMarkovStates().get(0).getId());
		assertEquals("state_3", behavior.getMarkovStates().get(1).getId());
		
		assertEquals(1, state1.getTransitions().size());
		assertEquals("state_3", state1.getTransitions().get(0).getTargetState());
		assertEquals(1.0, state1.getTransitions().get(0).getProbability(), 0.000001);
		
		assertNull(state3.getTransitions());
	}
	
	@Test
	public void testAdaptTransitionsWithCopiedTransitions() throws Exception {
		Behavior behavior = new Behavior();
		behavior.setName("test_behavior");
		behavior.setProbability(1.0);		
		List<MarkovState> markovStates = new ArrayList<MarkovState>();
		behavior.setMarkovStates(markovStates);
		
		MarkovState state1 = this.createMarkovState("state_1");
		state1.setTransitions(this.getTransitions(this.createTransition(0.1, "state_1"), this.createTransition(0.5, "state_2"), this.createTransition(0.4, "state_3")));
		markovStates.add(state1);
		
		MarkovState removedState = this.createMarkovState("state_2");
		removedState.setTransitions(this.getTransitions(this.createTransition(0.4, "state_3"), this.createTransition(0.6, "state_4")));
		
		MarkovState state3 = this.createMarkovState("state_3");
		state3.setTransitions(this.getTransitions(this.createTransition(1.0, "state_2")));
		markovStates.add(state3);
		
		MarkovState state4 = this.createMarkovState("state_4");
		markovStates.add(state4);
		
		Whitebox.invokeMethod(transformater, "adaptTransitions", behavior, removedState);
		
		assertEquals(3, behavior.getMarkovStates().size());
		
		assertEquals("state_1", behavior.getMarkovStates().get(0).getId());
		assertEquals("state_3", behavior.getMarkovStates().get(1).getId());
		assertEquals("state_4", behavior.getMarkovStates().get(2).getId());
		
		String[] state1_target = {"state_1", "state_3", "state_4"};
		Double[] state1_probabilities = {0.1, 0.6, 0.3};
		checkTransitions(state1.getTransitions(), state1_target, state1_probabilities);

		String[] state3_target = {"state_3", "state_4"};
		Double[] state3_probabilities = {0.4, 0.6};
		checkTransitions(state3.getTransitions(), state3_target, state3_probabilities);
		
		assertNull(state4.getTransitions());	
	}
	
	@Test
	public void testAdaptTransitionsWithLoop() throws Exception {
		Behavior behavior = new Behavior();
		behavior.setName("test_behavior");
		behavior.setProbability(1.0);		
		List<MarkovState> markovStates = new ArrayList<MarkovState>();
		behavior.setMarkovStates(markovStates);
		
		MarkovState state1 = this.createMarkovState("state_1");
		state1.setTransitions(this.getTransitions(
				this.createTransition(0.1, "state_1"), 
				this.createTransition(0.5, "state_2"), 
				this.createTransition(0.3, "state_3"), 
				this.createTransition(0.1, "state_4")));
		markovStates.add(state1);
		
		MarkovState removedState = this.createMarkovState("state_2");
		removedState.setTransitions(this.getTransitions(this.createTransition(0.2, "state_1"), this.createTransition(0.2, "state_2"), this.createTransition(0.6, "state_3")));
		
		MarkovState state3 = this.createMarkovState("state_3");
		state3.setTransitions(this.getTransitions(this.createTransition(1.0, "state_2")));
		markovStates.add(state3);
		
		MarkovState state4 = this.createMarkovState("state_4");
		markovStates.add(state4);
		
		Whitebox.invokeMethod(transformater, "adaptTransitions", behavior, removedState);
		
		assertEquals(3, behavior.getMarkovStates().size());
		
		assertEquals("state_1", behavior.getMarkovStates().get(0).getId());
		assertEquals("state_3", behavior.getMarkovStates().get(1).getId());
		
		String[] state1_target = {"state_1", "state_3", "state_4"};
		Double[] state1_probabilities = {0.225, 0.675, 0.1};
		checkTransitions(state1.getTransitions(), state1_target, state1_probabilities);
		
		String[] state3_target = {"state_1", "state_3"};
		Double[] state3_probabilities = {0.25, 0.75};
		checkTransitions(state3.getTransitions(), state3_target, state3_probabilities);
		
		assertNull(state4.getTransitions());	
	}
	
	@Test
	public void testGetIntersectionIdpaApplicationModel() {
		Application applicationInput1 = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_1);
		Application applicationInput2 = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_2);
		
		Application application = transformater.getIntersectionIdpaApplicationModel(applicationInput1, applicationInput2);
		
		assertNotNull(application);
		assertEquals(2, application.getEndpoints().size());
		assertTrue(application.getEndpoints().stream().filter(e -> e.getId().equals("buyUsingGET")).findAny().isPresent());
	}
	
	@Test
	public void testGetMatchedIdpaAnnotationModel() {
		
		ApplicationAnnotation annotationInput = testHelper.getIdpaModelFromFile(ApplicationAnnotation.class, BenchFlowTestHelper.TEST_IDPA_ANNOTATION_FILE_1);
		Application applicationInput1 = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_1);
		Application applicationInput2 = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_2);
		
		Application application = transformater.getIntersectionIdpaApplicationModel(applicationInput1, applicationInput2);
		ApplicationAnnotation annotation = transformater.getMatchedIdpaAnnotationModel(application, annotationInput);
		
		assertNotNull(annotation);
		assertEquals(2, annotation.getEndpointAnnotations().size());
		assertTrue(annotation.getEndpointAnnotations().stream().anyMatch(e -> e.getAnnotatedEndpoint().getId().equals("buyUsingGET")));
		assertTrue(annotation.getEndpointAnnotations().stream().anyMatch(e -> e.getAnnotatedEndpoint().getId().equals("shopUsingGET")));
	}
	
	@Test
	public void testGetValidBehaviorModelFromApplicationModel() throws IOException {

		BehaviorModel behaviorModelInput = testHelper.getBehaviorModelFromFile(BenchFlowTestHelper.TEST_BEHAVIOR_FILE_1);
		Application applicationInput1 = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_1);
		Application applicationInput2 = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_2);
		
		Application application = transformater.getIntersectionIdpaApplicationModel(applicationInput1, applicationInput2);
		BehaviorModel behavior = transformater.getValidBehaviorModelFromApplicationModel(behaviorModelInput, application);
		
		assertNotNull(behavior);
		assertEquals(3, behavior.getBehaviors().size());
		
		for(Behavior testBehavior : behavior.getBehaviors()) {
			assertEquals(3, testBehavior.getMarkovStates().size());
			
			assertTrue(testBehavior.getMarkovStates().stream().anyMatch(s -> s.getId().equals("INITIAL")));
			assertTrue(testBehavior.getMarkovStates().stream().anyMatch(s -> s.getId().equals("buyUsingGET")));
			assertTrue(testBehavior.getMarkovStates().stream().anyMatch(s -> s.getId().equals("shopUsingGET")));
			
			assertEquals("INITIAL", testBehavior.getInitialState());
		}
		
		MarkovState state1behavior1 = behavior.getBehaviors().get(0).getMarkovStates().stream().filter(s -> s.getId().equals("INITIAL")).findAny().get();
		MarkovState state2behavior1 = behavior.getBehaviors().get(0).getMarkovStates().stream().filter(s -> s.getId().equals("buyUsingGET")).findAny().get();
		MarkovState state3behavior1 = behavior.getBehaviors().get(0).getMarkovStates().stream().filter(s -> s.getId().equals("shopUsingGET")).findAny().get();
		assertEquals(1, state1behavior1.getTransitions().size());
		assertTransition(state1behavior1, "buyUsingGET", 0.9);
		
		assertEquals(2, state2behavior1.getTransitions().size());
		assertTransition(state2behavior1, "shopUsingGET", 0.2);
		assertTransition(state2behavior1, "buyUsingGET", 0.27);
		
		assertEquals(1, state3behavior1.getTransitions().size());
		assertTransition(state3behavior1, "shopUsingGET", 0.7);
			
		MarkovState state1behavior2 = behavior.getBehaviors().get(1).getMarkovStates().stream().filter(s -> s.getId().equals("INITIAL")).findAny().get();
		MarkovState state2behavior2 = behavior.getBehaviors().get(1).getMarkovStates().stream().filter(s -> s.getId().equals("buyUsingGET")).findAny().get();
		MarkovState state3behavior2 = behavior.getBehaviors().get(1).getMarkovStates().stream().filter(s -> s.getId().equals("shopUsingGET")).findAny().get();
		assertEquals(2, state1behavior2.getTransitions().size());
		assertTransition(state1behavior2, "shopUsingGET", 0.7);
		assertTransition(state1behavior2, "buyUsingGET", 0.3);
		
		assertEquals(1, state2behavior2.getTransitions().size());
		assertTransition(state2behavior2, "buyUsingGET", 0.65625);
		
		assertNull(state3behavior2.getTransitions());
		
		MarkovState state1behavior3 = behavior.getBehaviors().get(2).getMarkovStates().stream().filter(s -> s.getId().equals("INITIAL")).findAny().get();
		MarkovState state2behavior3 = behavior.getBehaviors().get(2).getMarkovStates().stream().filter(s -> s.getId().equals("buyUsingGET")).findAny().get();
		MarkovState state3behavior3 = behavior.getBehaviors().get(2).getMarkovStates().stream().filter(s -> s.getId().equals("shopUsingGET")).findAny().get();
		assertEquals(1, state1behavior3.getTransitions().size());
		assertTransition(state1behavior3, "shopUsingGET", 0.68);
		
		assertEquals(1, state2behavior3.getTransitions().size());
		assertTransition(state2behavior3, "shopUsingGET", 0.62);
		
		assertEquals(2, state3behavior3.getTransitions().size());
		assertTransition(state3behavior3, "shopUsingGET", 0.14);
		assertTransition(state3behavior3, "buyUsingGET", 0.4);
	}
	
	private void assertTransition(MarkovState state, String targetState, double probability) {
		Optional<Transition> optTransition = state.getTransitions().stream().filter(t -> t.getTargetState().equals(targetState)).findAny();
		assertTrue(String.format("No transition found from %s to %s.", state.getId(), targetState), optTransition.isPresent());
		assertEquals(probability, optTransition.get().getProbability(), 0.0000001);
	}
	
	@Test
	public void testMergeBehaviorModelsWithTheSameModel() {		
		
		BehaviorModel behavior = testHelper.getBehaviorModelFromFile(BenchFlowTestHelper.TEST_BEHAVIOR_FILE_1);
		
		// Test merge
		BehaviorModel resultBehavior = transformater.mergeBehaviorModels(behavior, behavior);
		assertEquals(3, resultBehavior.getBehaviors().size());
		assertEquals(4, resultBehavior.getBehaviors().get(0).getMarkovStates().size());
		assertEquals(4, resultBehavior.getBehaviors().get(1).getMarkovStates().size());
		assertEquals(5, resultBehavior.getBehaviors().get(2).getMarkovStates().size());
		
		assertEquals("behavior_model0", resultBehavior.getBehaviors().get(0).getName());
		assertEquals(0.48, resultBehavior.getBehaviors().get(0).getProbability(), 0.00000001);
		
		assertEquals("behavior_model1", resultBehavior.getBehaviors().get(1).getName());
		assertEquals(0.27, resultBehavior.getBehaviors().get(1).getProbability(), 0.00000001);
		
		assertEquals("behavior_model2", resultBehavior.getBehaviors().get(2).getName());
		assertEquals(0.25, resultBehavior.getBehaviors().get(2).getProbability(), 0.00000001);
		
		// Test correct object copy: Behavior
		assertEquals(3, behavior.getBehaviors().size());
		behavior.getBehaviors().add(new Behavior());
		assertEquals(4, behavior.getBehaviors().size());
		assertEquals(3, resultBehavior.getBehaviors().size());
		
		// Test correct object copy: MarkovState
		assertEquals(4, behavior.getBehaviors().get(0).getMarkovStates().size());
		behavior.getBehaviors().get(0).getMarkovStates().add(new MarkovState());
		assertEquals(5, behavior.getBehaviors().get(0).getMarkovStates().size());
		assertEquals(4, resultBehavior.getBehaviors().get(0).getMarkovStates().size());
		
		// Test correct object copy: Transition
		assertEquals(1, behavior.getBehaviors().get(0).getMarkovStates().get(0).getTransitions().size());
		behavior.getBehaviors().get(0).getMarkovStates().get(0).getTransitions().add(new Transition());
		assertEquals(2, behavior.getBehaviors().get(0).getMarkovStates().get(0).getTransitions().size());
		assertEquals(1, resultBehavior.getBehaviors().get(0).getMarkovStates().get(0).getTransitions().size());
	}
	
	@Test
	public void testMergeBehaviorModelsWithDifferentModels() {

		// Test values
		BehaviorModel behavior = testHelper.getBehaviorModelFromFile(BenchFlowTestHelper.TEST_BEHAVIOR_FILE_1);
		BehaviorModel behavior2 = new BehaviorModel();
		behavior2.setBehaviors(Lists.newArrayList(new Behavior("behavior_model1", "loginUsingPOST", 1.0, 
				Lists.newArrayList(new MarkovState("loginUsingPOST", Lists.newArrayList(new Transition("loginUsingPOST", 0.3, 500.0, 0.0)))))));
		
		// Test merge
		BehaviorModel resultBehavior = transformater.mergeBehaviorModels(behavior, behavior2);
		List<Behavior> resultBehaviors = resultBehavior.getBehaviors();
		assertEquals(4, resultBehaviors.size());
		assertEquals(4, resultBehaviors.get(0).getMarkovStates().size());
		assertEquals(4, resultBehaviors.get(1).getMarkovStates().size());
		assertEquals(5, resultBehaviors.get(2).getMarkovStates().size());
		assertEquals(1, resultBehaviors.get(3).getMarkovStates().size());
		
		assertEquals("_1_behavior_model0", resultBehaviors.get(0).getName());
		assertEquals(0.24, resultBehaviors.get(0).getProbability(), 0.00000001);
		
		assertEquals("_1_behavior_model1", resultBehaviors.get(1).getName());
		assertEquals(0.135, resultBehaviors.get(1).getProbability(), 0.00000001);
		
		assertEquals("_1_behavior_model2", resultBehaviors.get(2).getName());
		assertEquals(0.125, resultBehaviors.get(2).getProbability(), 0.00000001);	

		assertEquals("_2_behavior_model1", resultBehaviors.get(3).getName());
		assertEquals(0.5, resultBehaviors.get(3).getProbability(), 0.00000001);
	}
	
	@Test
	public void testMergeBehaviorModelsWithThreeModels() {
		
		// Test values
		BehaviorModel behavior1 = new BehaviorModel();
		behavior1.setBehaviors(Lists.newArrayList(new Behavior("behavior_model0", "loginUsingPOST", 0.3, 
				Lists.newArrayList(new MarkovState("loginUsingPOST", Lists.newArrayList(new Transition("loginUsingPOST", 0.3, 500.0, 0.0))))),
				new Behavior("_1_behavior_model1", "loginUsingPOST", 0.7, null)));
		
		BehaviorModel behavior2 = new BehaviorModel();
		behavior2.setBehaviors(Lists.newArrayList(new Behavior("_2_behavior_model0", "loginUsingPOST", 1.0, 
				Lists.newArrayList(new MarkovState("loginUsingPOST", Lists.newArrayList(new Transition("loginUsingPOST", 0.3, 500.0, 0.0)))))));
		
		BehaviorModel behavior3 = new BehaviorModel();
		behavior3.setBehaviors(Lists.newArrayList(new Behavior("_3_behavior_model0", "loginUsingPOST", 1.0, 
				Lists.newArrayList(new MarkovState("loginUsingPOST", Lists.newArrayList(new Transition("loginUsingPOST", 0.3, 500.0, 0.0)))))));
		
		// Test first merge
		BehaviorModel resultBehavior = transformater.mergeBehaviorModels(behavior1, behavior2);
		List<Behavior> resultBehaviors = resultBehavior.getBehaviors();
		assertEquals(3, resultBehaviors.size());
		assertEquals(1, resultBehaviors.get(0).getMarkovStates().size());
		assertNull(resultBehaviors.get(1).getMarkovStates());
		assertEquals(1, resultBehaviors.get(2).getMarkovStates().size());
		
		assertEquals("_2_behavior_model0", resultBehaviors.get(0).getName());
		assertEquals(0.15, resultBehaviors.get(0).getProbability(), 0.00000001);
		
		assertEquals("_1_behavior_model1", resultBehaviors.get(1).getName());
		assertEquals(0.35, resultBehaviors.get(1).getProbability(), 0.00000001);
		
		assertEquals("_5_behavior_model0", resultBehaviors.get(2).getName());
		assertEquals(0.5, resultBehaviors.get(2).getProbability(), 0.00000001);	
		
		// Test second merge
		BehaviorModel resultBehavior2 = transformater.mergeBehaviorModels(resultBehavior, behavior3, 0.33);
		List<Behavior> resultBehaviors2 = resultBehavior2.getBehaviors();
		assertEquals(4, resultBehaviors2.size());
		assertEquals(1, resultBehaviors2.get(0).getMarkovStates().size());
		assertNull(resultBehaviors2.get(1).getMarkovStates());
		assertEquals(1, resultBehaviors2.get(2).getMarkovStates().size());
		assertEquals(1, resultBehaviors2.get(3).getMarkovStates().size());
		
		assertEquals("_2_behavior_model0", resultBehaviors2.get(0).getName());
		assertEquals(0.1005, resultBehaviors2.get(0).getProbability(), 0.00000001);
		
		assertEquals("_1_behavior_model1", resultBehaviors2.get(1).getName());
		assertEquals(0.2345, resultBehaviors2.get(1).getProbability(), 0.00000001);
		
		assertEquals("_5_behavior_model0", resultBehaviors2.get(2).getName());
		assertEquals(0.335, resultBehaviors2.get(2).getProbability(), 0.00000001);	
		
		assertEquals("_10_behavior_model0", resultBehaviors2.get(3).getName());
		assertEquals(0.33, resultBehaviors2.get(3).getProbability(), 0.00000001);	
	}
	
	@Test
	public void testGetIntersectionWorkloadFromVersions() {
		PowerMockito.replace(PowerMockito.method(TransformationExecutor.class, "getContinuITyModel")).with(
			new InvocationHandler() {
				
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					ApplicationAnnotation annotationModel = testHelper.getIdpaModelFromFile(ApplicationAnnotation.class, BenchFlowTestHelper.TEST_IDPA_ANNOTATION_FILE_1);
					BehaviorModel behavior = null;
					Application applicationInput = null;
					if(args[0].equals("heat-clinic") && args[1].equals("1.2.3")) {
						behavior = testHelper.getBehaviorModelFromFile(BenchFlowTestHelper.TEST_BEHAVIOR_FILE_1);
						applicationInput = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_1);
					} else if(args[0].equals("heat-clinic") && args[1].equals("1.2.4")) {	
						applicationInput = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_2);						
					} else {
						return null;
					}
					return new ContinuITyModel(behavior, applicationInput, annotationModel);
				}
			}
		);
		
		HttpWorkload workload = transformater.getIntersectionWorkloadFromVersions("heat-clinic", Lists.newArrayList("1.2.3", "1.2.4"), null);
		assertNotNull(workload);
		assertFalse(workload.sutVersion().isDefined());
		
		Map<String, HttpWorkloadItem> workloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(3, workloadItems.size());
		
		assertTrue(workloadItems.containsKey("behavior_model0"));
		assertTrue(workloadItems.containsKey("behavior_model1"));
		assertTrue(workloadItems.containsKey("behavior_model2"));
		
		assertEquals(0.48, workloadItems.get("behavior_model0").popularity().get().underlying(), 0.00000001);
		assertEquals(0.27, workloadItems.get("behavior_model1").popularity().get().underlying(), 0.00000001);
		assertEquals(0.25, workloadItems.get("behavior_model2").popularity().get().underlying(), 0.00000001);
		
		for(HttpWorkloadItem item : workloadItems.values()) {
			assertEquals(DriverType.HTTP, item.driverType());
			assertFalse(item.dataSources().isDefined());
			
			List<Operation> operations = new ArrayList<>(JavaConverters.asJavaCollection(item.operations().get()));
			assertEquals(3, operations.size());
			assertEquals("INITIAL_STATE", operations.get(0).id());
			assertEquals("buyUsingGET", operations.get(1).id());
			assertEquals("shopUsingGET", operations.get(2).id());
		}
	}
}
