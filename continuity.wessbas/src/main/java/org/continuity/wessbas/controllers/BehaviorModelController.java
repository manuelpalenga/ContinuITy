package org.continuity.wessbas.controllers;

import static org.continuity.api.rest.RestApi.Wessbas.BehaviorModel.ROOT;
import static org.continuity.api.rest.RestApi.Wessbas.BehaviorModel.Paths.CREATE;
import static org.continuity.api.rest.RestApi.Wessbas.BehaviorModel.Paths.UPLOAD;
import static org.continuity.api.rest.RestApi.Wessbas.BehaviorModel.Paths.DELETE;

import java.util.Map;

import org.continuity.api.entities.artifact.BehaviorModel;
import org.continuity.commons.storage.MemoryStorage;
import org.continuity.commons.storage.MixedStorage;
import org.continuity.wessbas.entities.WessbasBundle;
import org.continuity.wessbas.transform.benchflow.WessbasToBehaviorModelConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import m4jdsl.WorkloadModel;

/**
 * Controls the creation of {@link BehaviorModel} from a stored WESSBAS model.
 * 
 * @author Manuel Palenga
 *
 */
@RestController
@RequestMapping(ROOT)
public class BehaviorModelController {

	private static final Logger LOGGER = LoggerFactory.getLogger(JMeterController.class);

	@Autowired
	private MixedStorage<WessbasBundle> storageWessbas;
	
	@Autowired
	private MemoryStorage<BehaviorModel> storageBehavior;
	
	@Autowired
	private WessbasToBehaviorModelConverter behaviorModelConverter;
	
	/**
	 * Gets an behavior model of the model with the passed id and version.
	 * 
	 * @param tag
	 * 				The tag of the behavior model.
	 * @param version
	 * 				The version of the behavior model.
	 * @return The stored model or a 404 (Not Found) if there is no such model.
	 */
	@RequestMapping(value = CREATE, method = RequestMethod.GET)
	public ResponseEntity<BehaviorModel> getBehaviorModel(@PathVariable("tag") String tag, @RequestParam("version") String version) {
		if (tag == null) {
			throw new IllegalArgumentException("The workload model tag is null!");
		}
		
		String id = String.format("%s-%s", tag, version);
		BehaviorModel storedBehaviorModel = storageBehavior.get(id);
		if(storedBehaviorModel != null) {
			LOGGER.debug("Use stored behavior model with id {}.", id);
			return ResponseEntity.ok(storedBehaviorModel);
		}

		WessbasBundle wessbasBundleEntry = null;
		
		for(Map.Entry<String, WessbasBundle> wessbasEntry : storageWessbas.getAll().entrySet()) {
			
			String wessbasId = wessbasEntry.getKey();
			if(!(wessbasId.startsWith(tag + "-") && wessbasId.lastIndexOf("-") == tag.length())) {
				continue;
			}
			
			String wessbasVersion = wessbasEntry.getValue().getVersion();
			if(!(wessbasVersion != null && wessbasVersion.equals(version))) {
				continue;
			}
			
			wessbasBundleEntry = wessbasEntry.getValue();
			break;
		}
		
		if(wessbasBundleEntry == null) {
			return ResponseEntity.notFound().build();			
		}

		WorkloadModel workloadModel = wessbasBundleEntry.getWorkloadModel();

		BehaviorModel behaviorModel = behaviorModelConverter.convertToBehaviorModel(workloadModel);

		LOGGER.info("Created behavior model with id {}.", tag);

		return ResponseEntity.ok(behaviorModel);
	}
	
	/**
	 * Stores the provided behavior model with the defined tag and version.
	 * 
	 * @param tag 
	 * 				The tag of the stored model.
	 * @param version 
	 * 				The version of the stored model.
	 * @param behaviorModel
	 * 				The behavior model.
	 * @return The id of the new stored model.
	 */
	@RequestMapping(path = UPLOAD, method = RequestMethod.PUT)
	public ResponseEntity<String> uploadModel(@PathVariable String tag, @RequestParam String version, @RequestBody BehaviorModel behaviorModel) {
		String id = String.format("%s-%s", tag, version);
		storageBehavior.putToReserved(id, behaviorModel);

		return ResponseEntity.ok(id);
	}
	
	/**
	 * Deletes the stored behavior model with the defined tag and version.
	 * 
	 * @param tag 
	 * 				The tag of the stored model.
	 * @param version 
	 * 				The version of the stored model.
	 * @return The id of the removed model.
	 */
	@RequestMapping(path = DELETE, method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteModel(@PathVariable String tag, @RequestParam String version) {
		String id = String.format("%s-%s", tag, version);
		boolean success = storageBehavior.remove(id);
		if(success) {
			return ResponseEntity.ok(id);
		} else {
			return ResponseEntity.notFound().build();	
		}
	}
}
