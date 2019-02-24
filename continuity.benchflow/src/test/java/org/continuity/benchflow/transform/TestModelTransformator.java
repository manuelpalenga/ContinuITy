package org.continuity.benchflow.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.continuity.api.entities.artifact.BehaviorModel;
import org.continuity.api.entities.artifact.BehaviorModel.Behavior;
import org.continuity.benchflow.BenchFlowTestHelper;
import org.continuity.benchflow.artifact.ContinuITyModel;
import org.continuity.idpa.annotation.ApplicationAnnotation;
import org.continuity.idpa.application.Application;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import cloud.benchflow.dsl.definition.workload.HttpWorkload;
import cloud.benchflow.dsl.definition.workload.datasource.DataSource;
import cloud.benchflow.dsl.definition.workload.drivertype.DriverType;
import cloud.benchflow.dsl.definition.workload.interoperationtimingstype.InterOperationsTimingType;
import cloud.benchflow.dsl.definition.workload.mix.MatrixMix;
import cloud.benchflow.dsl.definition.workload.mix.Mix;
import cloud.benchflow.dsl.definition.workload.mix.transition.Transition;
import cloud.benchflow.dsl.definition.workload.operation.Operation;
import cloud.benchflow.dsl.definition.workload.operation.body.Body;
import cloud.benchflow.dsl.definition.workload.operation.body.BodyForm;
import cloud.benchflow.dsl.definition.workload.operation.extraction.Extraction;
import cloud.benchflow.dsl.definition.workload.operation.method.Method;
import cloud.benchflow.dsl.definition.workload.operation.parameter.Parameter;
import cloud.benchflow.dsl.definition.workload.operation.protocol.Protocol;
import cloud.benchflow.dsl.definition.workload.workloaditem.HttpWorkloadItem;
import scala.collection.JavaConverters;
import scala.collection.Seq;

public class TestModelTransformator {

	private ModelTransformator transformater;
	
	@Before
	public void setUp() {
		transformater = new ModelTransformator();
	}
	
	private BehaviorModel.Transition createTransition(final double probability, final String targetState){
		BehaviorModel.Transition transition = new BehaviorModel.Transition();
		transition.setProbability(probability);
		transition.setTargetState(targetState);
		return transition;
	}
	
	private BehaviorModel.MarkovState createMarkovState(final String id){
		BehaviorModel.MarkovState state = new BehaviorModel.MarkovState();
		state.setId(id);
		return state;
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testEmptyContinuITyModel() {
		transformater.transformToBenchFlow(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testEmptyModels() {
		transformater.transformToBenchFlow(new ContinuITyModel(null, null, null));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetBehaviorWithoutBehaviors() throws Exception {
		Behavior behavior = new Behavior();
		Whitebox.invokeMethod(transformater, "getBehavior", behavior);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetBehaviorWithMissingInitialState() throws Exception {

		Behavior behavior = new Behavior();
		behavior.setName("test_behavior");
		behavior.setProbability(1.0);
		
		List<BehaviorModel.MarkovState> markovStates = new ArrayList<BehaviorModel.MarkovState>();
		markovStates.add(this.createMarkovState("state_1"));	
		behavior.setMarkovStates(markovStates);

		Whitebox.invokeMethod(transformater, "getBehavior", behavior);
	}
	
	@Test
	public void testGetBehaviorWithOneMarkovState() throws Exception {

		Behavior behavior = new Behavior();
		behavior.setName("test_behavior");
		behavior.setProbability(1.0);
		behavior.setInitialState("state_1");
		
		List<BehaviorModel.MarkovState> markovStates = new ArrayList<BehaviorModel.MarkovState>();
		markovStates.add(this.createMarkovState("state_1"));
		behavior.setMarkovStates(markovStates);

		Mix result = Whitebox.invokeMethod(transformater, "getBehavior", behavior);

		final double[][] PROBABILITY  = 
		{{0.0}};
		
		final Double[][] MEAN  = 
		{{null}};
		
		final Double[][] DEVIATION  = 
		{{null}};
				
		this.checkMix(result, PROBABILITY, MEAN, DEVIATION);
	}
	
	@Test
	public void testGetBehaviorWithTwoMarkovStates() throws Exception {

		Behavior behavior = new Behavior();
		behavior.setName("test_behavior");
		behavior.setProbability(1.0);
		behavior.setInitialState("state_2");
		
		List<BehaviorModel.MarkovState> markovStates = new ArrayList<BehaviorModel.MarkovState>();
		markovStates.add(this.createMarkovState("state_1"));
		markovStates.add(this.createMarkovState("state_2"));
		
		List<BehaviorModel.Transition> transitions = new ArrayList<BehaviorModel.Transition>();
		transitions.add(this.createTransition(0.7, "state_2"));	
		BehaviorModel.Transition transition = this.createTransition(0.3, "state_1");
		transition.setMean(1024.512);
		transition.setDeviation(12.34);
		transitions.add(transition);
		markovStates.get(1).setTransitions(transitions);
		
		behavior.setMarkovStates(markovStates);
		
		Mix result = Whitebox.invokeMethod(transformater, "getBehavior", behavior);
		
		final double[][] PROBABILITY  = 
		{{0.7, 0.3},
		{0.0, 0.0}};
		
		final Double[][] MEAN  = 
		{{null, 1024.512},
		{null, null}};
		
		final Double[][] DEVIATION  = 
		{{null, 12.34},
		{null, null}};
				
		this.checkMix(result, PROBABILITY, MEAN, DEVIATION);
	}
	
	@Test
	public void testGetBehaviorWithFourMarkovStates() throws Exception {

		Behavior behavior = new Behavior();
		behavior.setName("test_behavior");
		behavior.setProbability(1.0);
		behavior.setInitialState("state_1");
		
		List<BehaviorModel.MarkovState> markovStates = new ArrayList<BehaviorModel.MarkovState>();
		markovStates.add(this.createMarkovState("state_1"));
		markovStates.add(this.createMarkovState("state_2"));
		markovStates.add(this.createMarkovState("state_3"));
		markovStates.add(this.createMarkovState("state_4"));
		
		List<BehaviorModel.Transition> transitions1 = new ArrayList<BehaviorModel.Transition>();
		transitions1.add(this.createTransition(0.3, "state_2"));	
		transitions1.add(this.createTransition(0.7, "state_3"));	
		markovStates.get(0).setTransitions(transitions1);
		
		List<BehaviorModel.Transition> transitions3 = new ArrayList<BehaviorModel.Transition>();
		BehaviorModel.Transition transition = this.createTransition(1.0, "state_4");
		transition.setMean(128.0);
		transitions3.add(transition);	
		markovStates.get(2).setTransitions(transitions3);
		
		behavior.setMarkovStates(markovStates);
		
		Mix result = Whitebox.invokeMethod(transformater, "getBehavior", behavior);
		
		final double[][] PROBABILITY  = 
		{{0.0, 0.3, 0.7, 0.0},
		{0.0, 0.0, 0.0, 0.0},
		{0.0, 0.0, 0.0, 1.0},
		{0.0, 0.0, 0.0, 0.0}};
		
		final Double[][] MEAN  = 
		{{null, null, null, null},
		{null, null, null, null},
		{null, null, null, 128.0},
		{null, null, null, null}};
		
		final Double[][] DEVIATION  = 
		{{null, null, null, null},
		{null, null, null, null},
		{null, null, null, 0.0},
		{null, null, null, null}};
				
		this.checkMix(result, PROBABILITY, MEAN, DEVIATION);
	}
	
	private void testBehavior1(HttpWorkloadItem workloadItem) {
		// Check Operations
		List<Operation> operations = getJavaListOperation(workloadItem.operations().get());
		assertEquals(3, operations.size());
		
		assertEquals("startUsingOPTIONS", operations.get(0).id());
		assertEquals("productUsingPOST", operations.get(1).id());
		assertEquals("logoutUsingGET", operations.get(2).id());
		
		for(Operation operation : operations) {
			assertFalse(operation.body().isDefined());
			assertFalse(operation.jsonExtraction().isDefined());
			assertFalse(operation.regexExtraction().isDefined());
			assertFalse(operation.headers().isDefined());
			assertFalse(operation.urlParameter().isDefined());
			assertFalse(operation.queryParameter().isDefined());
			assertEquals(Protocol.HTTP, operation.protocol());
		}
		
		Operation operationStart = operations.get(0);
		Operation operationProduct = operations.get(1);
		Operation operationLogout = operations.get(2);
				
		assertEquals(Method.OPTIONS, operationStart.method());
		assertEquals(Method.POST, operationProduct.method());
		assertEquals(Method.GET, operationLogout.method());
		
		assertEquals("/index.html/start", operationStart.endpoint());
		assertEquals("/product", operationProduct.endpoint());
		assertEquals("/logout", operationLogout.endpoint());
		
		final double[][] PROBABILITY  = 
		{{0.0, 0.7, 0.3},
		{0.0, 0.0, 0.0},
		{0.0, 0.0, 0.0}};
				
		this.checkMix(workloadItem, PROBABILITY);		
	}
	
	private void testBehavior2(HttpWorkloadItem workloadItem) {
		// Check Operations
		List<Operation> operations = getJavaListOperation(workloadItem.operations().get());
		assertEquals(2, operations.size());
		
		assertEquals("productUsingPOST", operations.get(0).id());
		assertEquals("searchUsingPOST", operations.get(1).id());
		
		for(Operation operation : operations) {
			assertFalse(operation.jsonExtraction().isDefined());
			assertFalse(operation.regexExtraction().isDefined());
			assertFalse(operation.headers().isDefined());
			assertFalse(operation.urlParameter().isDefined());
			assertFalse(operation.queryParameter().isDefined());
			assertEquals(Protocol.HTTP, operation.protocol());
		}
		
		Operation operationProduct = operations.get(0);
		Operation operationSearch = operations.get(1);
		
		assertEquals(Method.POST, operationProduct.method());
		assertEquals(Method.POST, operationSearch.method());
		
		assertEquals("/product", operationProduct.endpoint());
		assertEquals("/search", operationSearch.endpoint());
		
		assertFalse(operationProduct.body().isDefined());
		assertTrue(operationSearch.body().isDefined());
		
		// Check body parameter
		assertTrue(operationSearch.body().get() instanceof BodyForm);
		
		BodyForm bodyForm = (BodyForm)operationSearch.body().get();
		Map<String, Parameter> mapSearchParameter = JavaConverters.mapAsJavaMap(bodyForm.body());
		assertEquals(2, mapSearchParameter.size());
		assertTrue(mapSearchParameter.containsKey("item"));
		assertTrue(mapSearchParameter.containsKey("color"));
		
		assertFalse(mapSearchParameter.get("item").retrieval().isDefined());
		assertFalse(mapSearchParameter.get("color").retrieval().isDefined());
		
		this.checkParameterValues(mapSearchParameter.get("item"), "42");
		this.checkParameterValues(mapSearchParameter.get("color"), "black", "red", "blue");
		
		final double[][] PROBABILITY  = 
		{{0.0, 0.9},
		{0.0, 0.0}};
				
		this.checkMix(workloadItem, PROBABILITY);
		
	}
	
	@Test
	public void testTransformToBenchFlowWithOneBehaviorAndNoParameter() {
		
		// Init test
		BehaviorModelGenerator behaviorModelGenerator = new BehaviorModelGenerator();
		ContinuITyIDPAGenerator idpaGenerator = new ContinuITyIDPAGenerator();
		
		BehaviorModel behaviorModel = behaviorModelGenerator.createBehaviorModel(1);
		Application application = idpaGenerator.setupApplication();
		ApplicationAnnotation annotation = idpaGenerator.setupAnnotation(application);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, application, annotation);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertFalse(workload.sutVersion().isDefined());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(1, mapWorkloadItems.size());
		assertTrue(mapWorkloadItems.containsKey("Behavior_WithoutParameter"));

		HttpWorkloadItem workloadItem = mapWorkloadItems.get("Behavior_WithoutParameter");
		assertEquals(DriverType.HTTP, workloadItem.driverType());
		assertFalse(workloadItem.dataSources().nonEmpty());
		assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
		assertFalse(workloadItem.popularity().isDefined());

		this.testBehavior1(workloadItem);
	}
	
	@Test
	public void testTransformToBenchFlowWithTwoBehaviorAndFormParameter() {
		
		// Init test
		BehaviorModelGenerator behaviorModelGenerator = new BehaviorModelGenerator();
		ContinuITyIDPAGenerator idpaGenerator = new ContinuITyIDPAGenerator();
		
		BehaviorModel behaviorModel = behaviorModelGenerator.createBehaviorModel(2);
		Application application = idpaGenerator.setupApplication();
		ApplicationAnnotation annotation = idpaGenerator.setupAnnotation(application);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, application, annotation);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertFalse(workload.sutVersion().isDefined());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(2, mapWorkloadItems.size());
		assertTrue(mapWorkloadItems.containsKey("Behavior_WithoutParameter"));
		assertTrue(mapWorkloadItems.containsKey("Behavior_FormParameter"));

		for(HttpWorkloadItem workloadItem : mapWorkloadItems.values()) {
			assertEquals(DriverType.HTTP, workloadItem.driverType());
			assertFalse(workloadItem.dataSources().nonEmpty());
			assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
		}
		
		HttpWorkloadItem workloadItemBehavior0 = mapWorkloadItems.get("Behavior_WithoutParameter");
		HttpWorkloadItem workloadItemBehavior1 = mapWorkloadItems.get("Behavior_FormParameter");
		
		assertEquals(0.2, workloadItemBehavior0.popularity().get().underlying(), 0.000001);
		assertEquals(0.8, workloadItemBehavior1.popularity().get().underlying(), 0.000001);

		this.testBehavior1(workloadItemBehavior0);
		this.testBehavior2(workloadItemBehavior1);
	}
	
	@Test
	public void testTransformToBenchFlowWithDifferentParameters() {
		
		// Init test
		BehaviorModelGenerator behaviorModelGenerator = new BehaviorModelGenerator();
		ContinuITyIDPAGenerator idpaGenerator = new ContinuITyIDPAGenerator();
		
		BehaviorModel behaviorModel = behaviorModelGenerator.createBehaviorModel(3);
		Application application = idpaGenerator.setupApplication();
		application.setVersion("1.2.3");
		ApplicationAnnotation annotation = idpaGenerator.setupAnnotation(application);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, application, annotation);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertTrue(workload.sutVersion().isDefined());
		assertEquals("1.2.3", workload.sutVersion().get());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(1, mapWorkloadItems.size());
		assertTrue(mapWorkloadItems.containsKey("Behavior_DifferentParameter"));

		HttpWorkloadItem workloadItem = mapWorkloadItems.get("Behavior_DifferentParameter");
		assertEquals(DriverType.HTTP, workloadItem.driverType());
		assertFalse(workloadItem.dataSources().nonEmpty());
		assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
		assertFalse(workloadItem.popularity().isDefined());

		
		// Check Operations
		List<Operation> operations = getJavaListOperation(workloadItem.operations().get());
		assertEquals(2, operations.size());
		
		assertEquals("selectUsingGET", operations.get(0).id());
		assertEquals("itemSelectionUsingPOST", operations.get(1).id());
		
		for(Operation operation : operations) {
			assertFalse(operation.jsonExtraction().isDefined());
			assertFalse(operation.regexExtraction().isDefined());
			assertFalse(operation.headers().isDefined());
			assertTrue(operation.urlParameter().isDefined());
		}
		
		Operation operationSelect = operations.get(0);
		Operation operationItem = operations.get(1);
		
		assertEquals(Protocol.HTTP, operationSelect.protocol());
		assertEquals(Protocol.HTTPS, operationItem.protocol());
				
		assertEquals(Method.GET, operationSelect.method());
		assertEquals(Method.POST, operationItem.method());
		
		assertEquals("/shop/${product}/select/${color}", operationSelect.endpoint());
		assertEquals("/itemselection/${category}", operationItem.endpoint());
		
		assertFalse(operationSelect.body().isDefined());
		assertTrue(operationItem.body().isDefined());
		
		assertFalse(operationSelect.queryParameter().isDefined());
		assertTrue(operationItem.queryParameter().isDefined());
		
		// Test endpoint with only url parameter
		Map<String, Parameter> mapSelectUrlParameter = JavaConverters.mapAsJavaMap(operationSelect.urlParameter().get());
		assertTrue(mapSelectUrlParameter.containsKey("product"));
		assertTrue(mapSelectUrlParameter.containsKey("color"));		
		this.checkParameterValues(mapSelectUrlParameter.get("product"), "car", "bike");
		this.checkParameterValues(mapSelectUrlParameter.get("color"), "black", "red", "blue");
		
		// Test endpoint with different parameter types
		Map<String, Parameter> mapItemUrlParameter = JavaConverters.mapAsJavaMap(operationItem.urlParameter().get());
		assertTrue(mapItemUrlParameter.containsKey("category"));	
		this.checkParameterValues(mapItemUrlParameter.get("category"), "top", "bottom");
		
		Map<String, Parameter> mapItemQueryParameter = JavaConverters.mapAsJavaMap(operationItem.queryParameter().get());
		assertTrue(mapItemQueryParameter.containsKey("id"));	
		this.checkParameterValues(mapItemQueryParameter.get("id"), "123");
		
		assertTrue(operationItem.body().get() instanceof BodyForm);
		BodyForm bodyForm = (BodyForm) operationItem.body().get();
		Map<String, Parameter> mapItemBodyFormParameter = JavaConverters.mapAsJavaMap(bodyForm.body());
		assertTrue(mapItemBodyFormParameter.containsKey("price"));		
		this.checkParameterValues(mapItemBodyFormParameter.get("price"), "0.12", "12.34", "987.65");

		
		final double[][] PROBABILITY  = 
		{{0.2, 0.8},
		{0.0, 0.0}};
				
		this.checkMix(workloadItem, PROBABILITY);
	}
	
	@Test
	public void testTransformToBenchFlowWithRegexParameters() {
		
		// Init test
		BehaviorModelGenerator behaviorModelGenerator = new BehaviorModelGenerator();
		ContinuITyIDPAGenerator idpaGenerator = new ContinuITyIDPAGenerator();
		
		BehaviorModel behaviorModel = behaviorModelGenerator.createBehaviorModel(4);
		Application application = idpaGenerator.setupApplication();
		application.setVersion("Version: 12.34.5");
		ApplicationAnnotation annotation = idpaGenerator.setupAnnotation(application);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, application, annotation);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertTrue(workload.sutVersion().isDefined());
		assertEquals("12.34.5", workload.sutVersion().get());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(1, mapWorkloadItems.size());
		assertTrue(mapWorkloadItems.containsKey("Behavior_RegexParameter"));

		HttpWorkloadItem workloadItem = mapWorkloadItems.get("Behavior_RegexParameter");
		assertEquals(DriverType.HTTP, workloadItem.driverType());
		assertFalse(workloadItem.dataSources().nonEmpty());
		assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
		assertFalse(workloadItem.popularity().isDefined());

		
		// Check Operations
		List<Operation> operations = getJavaListOperation(workloadItem.operations().get());
		assertEquals(3, operations.size());
		
		assertEquals("loginUsingPOST", operations.get(0).id());
		assertEquals("accountUsingPOST", operations.get(1).id());
		assertEquals("buyUsingGET", operations.get(2).id());
		
		for(Operation operation : operations) {
			assertFalse(operation.jsonExtraction().isDefined());
			assertFalse(operation.urlParameter().isDefined());
			assertEquals(Protocol.HTTP, operation.protocol());
		}
		
		Operation operationLogin = operations.get(0);		
		Operation operationAccount = operations.get(1);
		Operation operationBuy = operations.get(2);
				
		assertEquals(Method.POST, operationLogin.method());
		assertEquals(Method.POST, operationAccount.method());
		assertEquals(Method.GET, operationBuy.method());
		
		assertEquals("/login", operationLogin.endpoint());
		assertEquals("/account", operationAccount.endpoint());
		assertEquals("/buy", operationBuy.endpoint());
		
		assertTrue(operationLogin.headers().isDefined());
		assertFalse(operationAccount.headers().isDefined());
		assertFalse(operationBuy.headers().isDefined());
		
		assertTrue(operationLogin.queryParameter().isDefined());
		assertFalse(operationAccount.queryParameter().isDefined());
		assertTrue(operationBuy.queryParameter().isDefined());
		
		assertFalse(operationLogin.body().isDefined());
		assertTrue(operationAccount.body().isDefined());
		assertFalse(operationBuy.body().isDefined());
		
		assertTrue(operationLogin.regexExtraction().isDefined());
		assertTrue(operationAccount.regexExtraction().isDefined());
		assertFalse(operationBuy.regexExtraction().isDefined());
		
		// Check headers
		Map<String, String> mapLoginHeaders = JavaConverters.mapAsJavaMap(operationLogin.headers().get());
		assertEquals(1, mapLoginHeaders.size());
		assertTrue(mapLoginHeaders.containsKey("Content-Type"));
		assertEquals("application/x-www-form-urlencoded", mapLoginHeaders.get("Content-Type"));
		
		// Check regex
		Map<String, Extraction> mapLoginRegex = JavaConverters.mapAsJavaMap(operationLogin.regexExtraction().get());
		assertEquals(2, mapLoginRegex.size());
		assertTrue(mapLoginRegex.containsKey("Input_extracted_token"));
		assertTrue(mapLoginRegex.containsKey("Input_extracted_item"));	
		
		Extraction extractionLoginToken = mapLoginRegex.get("Input_extracted_token");	
		assertEquals("<input name=\"object\" type=\"hidden\" value=\"(.*)\"/>", extractionLoginToken.pattern());	
		assertEquals(4, extractionLoginToken.matchNumber().get());
		assertEquals("OBJECT_NOT_FOUND", extractionLoginToken.fallbackValue().get());
		
		Extraction extractionLoginItem = mapLoginRegex.get("Input_extracted_item");
		assertEquals("<input name=\"item\" type=\"hidden\" value=\"(.*)\"/>", extractionLoginItem.pattern());
		assertEquals(1 , extractionLoginItem.matchNumber().get());
		assertEquals("NOT FOUND", extractionLoginItem.fallbackValue().get());
		
		Map<String, Extraction> mapAccountRegex = JavaConverters.mapAsJavaMap(operationAccount.regexExtraction().get());
		assertEquals(1, mapAccountRegex.size());
		assertTrue(mapAccountRegex.containsKey("Input_extracted_token"));
		
		Extraction extractionAccountToken = mapAccountRegex.get("Input_extracted_token");
		assertEquals("<input id=\"select\" name=\"object\" type=\"hidden\" value=\"(.*)\"/>", extractionAccountToken.pattern());
		
		assertEquals(1, extractionAccountToken.matchNumber().get());
		assertEquals("NOT FOUND", extractionAccountToken.fallbackValue().get());	
		
		// Check query parameter
		Map<String, Parameter> mapLoginQueryParameter = JavaConverters.mapAsJavaMap(operationLogin.queryParameter().get());
		assertEquals(2, mapLoginQueryParameter.size());
		assertTrue(mapLoginQueryParameter.containsKey("user"));
		assertTrue(mapLoginQueryParameter.containsKey("password"));		
		this.checkParameterValues(mapLoginQueryParameter.get("user"), "foo", "bar");
		this.checkParameterValues(mapLoginQueryParameter.get("password"), "admin");
		
		// Check regex parameter
		Map<String, Parameter> mapBuyQueryParameter = JavaConverters.mapAsJavaMap(operationBuy.queryParameter().get());
		assertEquals(1, mapBuyQueryParameter.size());
		assertTrue(mapBuyQueryParameter.containsKey("token"));		
		this.checkParameterValues(mapBuyQueryParameter.get("token"), "${Input_extracted_token}");
		
		assertTrue(operationAccount.body().get() instanceof BodyForm);
		BodyForm bodyForm = (BodyForm) operationAccount.body().get();
		Map<String, Parameter> mapAccountBodyParameter = JavaConverters.mapAsJavaMap(bodyForm.body());
		assertEquals(2, mapAccountBodyParameter.size());
		assertTrue(mapAccountBodyParameter.containsKey("token"));
		assertTrue(mapAccountBodyParameter.containsKey("item"));
		this.checkParameterValues(mapAccountBodyParameter.get("token"), "${Input_extracted_token}");
		this.checkParameterValues(mapAccountBodyParameter.get("item"), "${Input_extracted_item}");
		
		
		final double[][] PROBABILITY  = 
		{{0.0, 0.9, 0.0},
		{0.0, 0.0, 0.9},
		{0.0, 0.0, 0.0}};
				
		this.checkMix(workloadItem, PROBABILITY);
	}
	
	@Test
	public void testTransformToBenchFlowWithBodyParameters() {
		
		// Init test
		BehaviorModelGenerator behaviorModelGenerator = new BehaviorModelGenerator();
		ContinuITyIDPAGenerator idpaGenerator = new ContinuITyIDPAGenerator();
		
		BehaviorModel behaviorModel = behaviorModelGenerator.createBehaviorModel(5);
		Application application = idpaGenerator.setupApplication();
		ApplicationAnnotation annotation = idpaGenerator.setupAnnotation(application);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, application, annotation);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertFalse(workload.sutVersion().isDefined());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(1, mapWorkloadItems.size());
		assertTrue(mapWorkloadItems.containsKey("Behavior_BodyParameter"));

		HttpWorkloadItem workloadItem = mapWorkloadItems.get("Behavior_BodyParameter");
		assertEquals(DriverType.HTTP, workloadItem.driverType());
		assertFalse(workloadItem.dataSources().nonEmpty());
		assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
		assertFalse(workloadItem.popularity().isDefined());

		
		// Check Operations
		List<Operation> operations = getJavaListOperation(workloadItem.operations().get());
		assertEquals(2, operations.size());
		
		assertEquals("convertUsingPOST", operations.get(0).id());
		assertEquals("transformUsingPOST", operations.get(1).id());
		
		for(Operation operation : operations) {
			assertEquals(Method.POST, operation.method());
			assertFalse(operation.headers().isDefined());		
			assertFalse(operation.jsonExtraction().isDefined());			
			assertFalse(operation.urlParameter().isDefined());
			assertFalse(operation.queryParameter().isDefined());
			assertEquals(Protocol.HTTP, operation.protocol());
			
			assertTrue(operation.body().isDefined());
		}
		
		Operation operationConvert = operations.get(0);
		Operation operationTransform = operations.get(1);
		
		assertEquals("/convert", operationConvert.endpoint());
		assertEquals("/transform", operationTransform.endpoint());
		
		assertTrue(operationConvert.regexExtraction().isDefined());
		assertFalse(operationTransform.regexExtraction().isDefined());
				
		// Check regex
		Map<String, Extraction> mapConvertRegex = JavaConverters.mapAsJavaMap(operationConvert.regexExtraction().get());
		assertEquals(1, mapConvertRegex.size());
		assertTrue(mapConvertRegex.containsKey("Input_extracted_content_json"));
		
		Extraction extractionContent = mapConvertRegex.get("Input_extracted_content_json");	
		assertEquals("<div id=\"result\" value=\"(.*)\"/>", extractionContent.pattern());	
		assertEquals("NOT FOUND", extractionContent.fallbackValue().get());
		
		// Check regex parameter
		assertTrue(operationTransform.body().get() instanceof Body);
		Body transformBody = (Body) operationTransform.body().get();
		this.checkParameterValues(transformBody.body(), "${Input_extracted_content_json}");
		
		// Check body data
		assertTrue(operationConvert.body().get() instanceof Body);
		Body convertBody = (Body) operationConvert.body().get();
		this.checkParameterValues(convertBody.body(), "<xml><p>Hello</p></xml>", "<xml><div>World</div></xml>");
		
		final double[][] PROBABILITY  = 
		{{0.0, 0.75},
		{0.0, 0.0}};
				
		this.checkMix(workloadItem, PROBABILITY);
	}
	
	@Test
	public void testTransformToBenchFlowWithInitialState() {
		
		// Init test
		BehaviorModelGenerator behaviorModelGenerator = new BehaviorModelGenerator();
		ContinuITyIDPAGenerator idpaGenerator = new ContinuITyIDPAGenerator();
		
		BehaviorModel behaviorModel = behaviorModelGenerator.createBehaviorModel(6);
		Application application = idpaGenerator.setupApplication();
		ApplicationAnnotation annotation = idpaGenerator.setupAnnotation(application);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, application, annotation);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertFalse(workload.sutVersion().isDefined());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(1, mapWorkloadItems.size());
		assertTrue(mapWorkloadItems.containsKey("Behavior_Initial"));

		HttpWorkloadItem workloadItem = mapWorkloadItems.get("Behavior_Initial");
		assertEquals(DriverType.HTTP, workloadItem.driverType());
		assertFalse(workloadItem.dataSources().nonEmpty());
		assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
		assertFalse(workloadItem.popularity().isDefined());

		// Check Operations
		List<Operation> operations = getJavaListOperation(workloadItem.operations().get());
		assertEquals(4, operations.size());
		
		assertEquals("INITIAL_STATE", operations.get(0).id());
		assertEquals("productUsingPOST", operations.get(1).id());
		assertEquals("logoutUsingGET", operations.get(2).id());
		assertEquals("startUsingOPTIONS", operations.get(3).id());
		
		for(Operation operation : operations) {
			assertFalse(operation.jsonExtraction().isDefined());
			assertFalse(operation.regexExtraction().isDefined());
			assertFalse(operation.headers().isDefined());
			assertFalse(operation.urlParameter().isDefined());
			assertFalse(operation.queryParameter().isDefined());
			assertFalse(operation.body().isDefined());
		}	
		
		Operation operationINITIAL = operations.get(0);
		Operation operationProduct = operations.get(1);
		Operation operationLogout = operations.get(2);
		Operation operationStart = operations.get(3);
		
		assertNull(operationINITIAL.endpoint());
		assertEquals("/product", operationProduct.endpoint());
		assertEquals("/logout", operationLogout.endpoint());
		assertEquals("/index.html/start", operationStart.endpoint());
		
		assertNull(operationINITIAL.protocol());
		assertEquals(Protocol.HTTP, operationProduct.protocol());
		assertEquals(Protocol.HTTP, operationLogout.protocol());
		assertEquals(Protocol.HTTP, operationStart.protocol());
		
		assertNull(operationINITIAL.method());
		assertEquals(Method.POST, operationProduct.method());
		assertEquals(Method.GET, operationLogout.method());
		assertEquals(Method.OPTIONS, operationStart.method());
		
		final double[][] PROBABILITY  = 
		{{0.0, 0.7, 0.2, 0.1},
		{0.0, 0.0, 0.0, 0.0},
		{0.0, 0.0, 0.0, 0.0},
		{0.0, 0.3, 0.0, 0.6}};
				
		this.checkMix(workloadItem, PROBABILITY);
	}
	
	@Test
	public void testTransformToBenchFlowWithCSVParameter() {
		
		// Init test
		BehaviorModelGenerator behaviorModelGenerator = new BehaviorModelGenerator();
		ContinuITyIDPAGenerator idpaGenerator = new ContinuITyIDPAGenerator();
		
		BehaviorModel behaviorModel = behaviorModelGenerator.createBehaviorModel(7);
		Application application = idpaGenerator.setupApplication();
		application.setVersion("1.2.3");
		ApplicationAnnotation annotation = idpaGenerator.setupAnnotation(application);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, application, annotation);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertTrue(workload.sutVersion().isDefined());
		assertEquals("1.2.3", workload.sutVersion().get());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(1, mapWorkloadItems.size());
		assertTrue(mapWorkloadItems.containsKey("Behavior_CSVFormParameter"));

		HttpWorkloadItem workloadItem = mapWorkloadItems.get("Behavior_CSVFormParameter");
		assertEquals(DriverType.HTTP, workloadItem.driverType());
		assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
		assertFalse(workloadItem.popularity().isDefined());
		
		// Check CSV file
		assertTrue(workloadItem.dataSources().isDefined());
		List<DataSource> dataSources = JavaConverters.seqAsJavaList(workloadItem.dataSources().get()); 
		assertEquals(1, dataSources.size());
		assertEquals("languages.csv", dataSources.get(0).path());
		assertEquals(",", dataSources.get(0).delimiter());
		assertFalse(dataSources.get(0).name().isDefined());
		assertFalse(dataSources.get(0).retrieval().isDefined());
		
		// Check Operations
		List<Operation> operations = getJavaListOperation(workloadItem.operations().get());
		assertEquals(2, operations.size());
		
		assertEquals("productUsingPOST", operations.get(0).id());
		assertEquals("languageUsingPOST", operations.get(1).id());
		
		for(Operation operation : operations) {
			assertFalse(operation.jsonExtraction().isDefined());
			assertFalse(operation.regexExtraction().isDefined());
			assertFalse(operation.headers().isDefined());
			assertFalse(operation.urlParameter().isDefined());
			assertFalse(operation.queryParameter().isDefined());
			assertEquals(Protocol.HTTP, operation.protocol());
			assertEquals(Method.POST, operation.method());
		}
		
		Operation operationProduct = operations.get(0);
		Operation operationLanguage = operations.get(1);
		
		assertEquals("/product", operationProduct.endpoint());
		assertEquals("/language", operationLanguage.endpoint());
		
		assertFalse(operationProduct.body().isDefined());
		assertTrue(operationLanguage.body().isDefined());
		
		assertTrue(operationLanguage.body().get() instanceof BodyForm);
		BodyForm bodyForm = (BodyForm) operationLanguage.body().get();
		Map<String, Parameter> mapLanguageBodyFormParameter = JavaConverters.mapAsJavaMap(bodyForm.body());
		assertEquals(2, mapLanguageBodyFormParameter.size());
		assertTrue(mapLanguageBodyFormParameter.containsKey("language"));		
		assertTrue(mapLanguageBodyFormParameter.containsKey("area"));		
		this.checkParameterValues(mapLanguageBodyFormParameter.get("language"), "${Input_csv_language}");
		this.checkParameterValues(mapLanguageBodyFormParameter.get("area"), "${Input_csv_language_area}");

		
		final double[][] PROBABILITY  = 
		{{0.0, 0.9},
		{0.0, 0.0}};
				
		this.checkMix(workloadItem, PROBABILITY);
	}
	
	@Test
	public void testModelToModelTransformation() {
		
		BenchFlowTestHelper testHelper = new BenchFlowTestHelper();

		ApplicationAnnotation annotationModel = testHelper.getIdpaModelFromFile(ApplicationAnnotation.class, BenchFlowTestHelper.TEST_IDPA_ANNOTATION_FILE_1);
		Application applicationModel = testHelper.getIdpaModelFromFile(Application.class, BenchFlowTestHelper.TEST_IDPA_APPLICATION_FILE_1);
		BehaviorModel behaviorModel = testHelper.getBehaviorModelFromFile(BenchFlowTestHelper.TEST_BEHAVIOR_FILE_1);
		
		ContinuITyModel continuITyModel = new ContinuITyModel(behaviorModel, applicationModel, annotationModel);
		HttpWorkload workload = transformater.transformToBenchFlow(continuITyModel);
		
		// Test workload and workload-items
		assertFalse(workload.dataSources().nonEmpty());
		assertFalse(workload.operations().nonEmpty());
		assertTrue(workload.workloads().nonEmpty());
		assertTrue(workload.sutVersion().isDefined());

		Map<String, HttpWorkloadItem> mapWorkloadItems = JavaConverters.mapAsJavaMap(workload.workloads());
		assertEquals(3, mapWorkloadItems.size());
		
		assertTrue(mapWorkloadItems.containsKey("behavior_model0"));
		assertTrue(mapWorkloadItems.containsKey("behavior_model1"));
		assertTrue(mapWorkloadItems.containsKey("behavior_model2"));

		for(HttpWorkloadItem workloadItem : mapWorkloadItems.values()) {
			assertEquals(DriverType.HTTP, workloadItem.driverType());
			assertFalse(workloadItem.dataSources().nonEmpty());
			assertEquals(InterOperationsTimingType.FIXED_TIME, workloadItem.interOperationTimings().get());
			assertTrue(workloadItem.popularity().isDefined());
		}
		
		// Check operations		
		for(Map.Entry<String, HttpWorkloadItem> entry : mapWorkloadItems.entrySet()) {
			List<Operation> operations = getJavaListOperation(entry.getValue().operations().get());
			
			if(entry.getKey().equals("behavior_model2")) {
				assertEquals(5, operations.size());			
				assertEquals("INITIAL_STATE", operations.get(0).id());
				assertEquals("buyUsingGET", operations.get(1).id());
				assertEquals("loginUsingPOST", operations.get(2).id());
				assertEquals("searchUsingGET", operations.get(3).id());
				assertEquals("shopUsingGET", operations.get(4).id());
			} else {
				assertEquals(4, operations.size());	
				assertEquals("loginUsingPOST", operations.get(0).id());
				assertEquals("buyUsingGET", operations.get(1).id());
				assertEquals("searchUsingGET", operations.get(2).id());
				assertEquals("shopUsingGET", operations.get(3).id());
			}
						
			for(Operation operation : operations) {
				if(operation.id().equals("INITIAL_STATE")) {
					assertNull(operation.protocol());
					assertNull(operation.endpoint());
					assertNull(operation.method());
					assertFalse(operation.headers().isDefined());
					assertFalse(operation.body().isDefined());
					assertFalse(operation.jsonExtraction().isDefined());
					assertFalse(operation.regexExtraction().isDefined());
					assertFalse(operation.urlParameter().isDefined());
					assertFalse(operation.queryParameter().isDefined());
					continue;
				}
				assertTrue(operation.headers().isDefined());
				assertFalse(operation.body().isDefined());
				assertFalse(operation.jsonExtraction().isDefined());
				assertEquals(Protocol.HTTP, operation.protocol());
				
				switch(operation.id()) {
					
				case "buyUsingGET":
					assertFalse(operation.regexExtraction().isDefined());
					assertFalse(operation.urlParameter().isDefined());
					assertFalse(operation.queryParameter().isDefined());
					assertEquals("/cart/mini", operation.endpoint());
					assertEquals(Method.GET, operation.method());
					break;
					
				case "loginUsingPOST":
					assertFalse(operation.regexExtraction().isDefined());
					assertFalse(operation.urlParameter().isDefined());
					assertTrue(operation.queryParameter().isDefined());
					assertEquals("/login_post.htm", operation.endpoint());
					assertEquals(Method.POST, operation.method());
					break;
					
				case "searchUsingGET":
					assertTrue(operation.regexExtraction().isDefined());
					assertFalse(operation.urlParameter().isDefined());
					assertTrue(operation.queryParameter().isDefined());
					assertEquals("/search", operation.endpoint());
					assertEquals(Method.GET, operation.method());
					break;
					
				case "shopUsingGET":
					assertFalse(operation.regexExtraction().isDefined());
					assertTrue(operation.urlParameter().isDefined());
					assertFalse(operation.queryParameter().isDefined());
					assertEquals("/shop-products/${category}", operation.endpoint());
					assertEquals(Method.GET, operation.method());
					break;
				}
			}	
		}
		
		final double[][] PROBABILITY_0  = 
		{{0.0, 0.9, 0.0, 0.0},
		{0.3, 0.0, 0.5, 0.2},
		{0.0, 0.0, 0.7, 0.0},
		{0.0, 0.0, 0.0, 0.7}};
				
		final Double[][] MEAN_0  = 
		{{null, 4055.0, null, null},
		{0.0, null, 0.0, 0.0},
		{null, null, 1095.0, null},
		{null, null, null, 543.0}};
		
		final Double[][] DEVIATION_0  = 
		{{null, 0.0, null, null},
		{0.0, null, 0.0, 0.0},
		{null, null, 0.0, null},
		{null, null, null, 12.0}};
		
		
		final double[][] PROBABILITY_1  = 
		{{0.0, 0.3, 0.0, 0.7},
		{0.0, 0.0, 0.75, 0.0},
		{0.0, 0.7, 0.2, 0.0},
		{0.0, 0.0, 0.0, 0.0}};
		
		final Double[][] MEAN_1  = 
		{{null, 0.0, null, 0.0},
		{null, null, 2157.0, null},
		{null, 2124.0, 3210.0, null},
		{null, null, null, null}};
		
		final Double[][] DEVIATION_1  = 
		{{null, 0.0, null, 0.0},
		{null, null, 0.0, null},
		{null, 127.0, 0.0, null},
		{null, null, null, null}};
		
		
		final double[][] PROBABILITY_2  = 
		{{0.0, 0.0, 0.1, 0.6, 0.2},
		{0.0, 0.0, 0.0, 0.6, 0.2},
		{0.0, 0.0, 0.0, 0.0, 0.6},
		{0.0, 0.0, 0.0, 0.0, 0.7},
		{0.0, 0.4, 0.0, 0.2, 0.0}};
		
		final Double[][] MEAN_2  = 
		{{null, null, 250.0, 2157.0, 150.0},
		{null, null, null, 2157.0, 150.0},
		{null, null, null, null, 0.0},
		{null, null, null, null, 650.77},
		{null, 0.0, null, 450.72, null}};
		
		final Double[][] DEVIATION_2  = 
		{{null, null, 0.0, 0.0, 0.0},
		{null, null, null, 0.0, 0.0},
		{null, null, null, null, 0.0},
		{null, null, null, null, 120.24},
		{null, 0.0, null, 48.91, null}};
			
		this.checkMix(mapWorkloadItems.get("behavior_model0"), PROBABILITY_0, MEAN_0, DEVIATION_0);
		this.checkMix(mapWorkloadItems.get("behavior_model1"), PROBABILITY_1, MEAN_1, DEVIATION_1);
		this.checkMix(mapWorkloadItems.get("behavior_model2"), PROBABILITY_2, MEAN_2, DEVIATION_2);
		
	}
		
	private void checkMix(Mix mix, final double[][] PROBABILITY, final Double[][] MEAN, final Double[][] DEVIATION) {
		
		final int SIZE = PROBABILITY.length;
		
		assertFalse(mix.maxDeviation().isDefined());
		assertTrue(mix.mix() instanceof MatrixMix);
		
		MatrixMix matrixMix = (MatrixMix) mix.mix();
		List<Seq<Transition>> listMatrixMix = JavaConverters.seqAsJavaList(matrixMix.mix());
		assertEquals(SIZE, listMatrixMix.size());
		listMatrixMix.forEach(m -> assertEquals(SIZE, m.size()));
		
		for(int i = 0; i < SIZE; i++) {
			List<Transition> convertedListMatrixMix = JavaConverters.seqAsJavaList(listMatrixMix.get(i));
			for(int j = 0; j < SIZE; j++) {
				Transition transition = convertedListMatrixMix.get(j);
				assertEquals(PROBABILITY[i][j], transition.percent(), 0.000001);
				if(MEAN != null) {
					assertEquals(MEAN[i][j] != null, transition.mean().isDefined());
					if(MEAN[i][j] != null) {
						assertEquals(MEAN[i][j], (double)transition.mean().get(), 0.000001);
					}		
				}
				if(DEVIATION != null) {
					assertEquals(DEVIATION[i][j] != null, transition.deviation().isDefined());
					if(DEVIATION[i][j] != null) {
						assertEquals(DEVIATION[i][j], (double)transition.deviation().get(), 0.000001);
					}		
				}
			}		
		}
	}
	
	private void checkMix(HttpWorkloadItem workloadItem, final double[][] PROBABILITY, final Double[][] MEAN, final Double[][] DEVIATION) {
		
		// Check mix
		assertTrue(workloadItem.mix().isDefined());
		Mix mix = workloadItem.mix().get();
		
		this.checkMix(mix, PROBABILITY, MEAN, DEVIATION);
	}
	
	
	private void checkMix(HttpWorkloadItem workloadItem, final double[][] PROBABILITY) {
		
		// Check mix
		assertTrue(workloadItem.mix().isDefined());
		Mix mix = workloadItem.mix().get();
		
		this.checkMix(mix, PROBABILITY, null, null);
	}

	private void checkParameterValues(final Parameter parameter, final String... values) {
		List<String> parameterValues = JavaConverters.seqAsJavaList(parameter.items());
		assertEquals(values.length, parameterValues.size());
		for(int i = 0; i < values.length; i++) {
			assertEquals(values[i], parameterValues.get(i));
		}
	}
	
	private List<Operation> getJavaListOperation(scala.collection.immutable.List<Operation> scalaList){
		return new ArrayList<>(JavaConverters.asJavaCollection(scalaList));
	}
}
