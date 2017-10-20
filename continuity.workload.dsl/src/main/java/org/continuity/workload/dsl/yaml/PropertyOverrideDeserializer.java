package org.continuity.workload.dsl.yaml;

import java.io.IOException;

import org.continuity.workload.dsl.annotation.PropertyOverride;
import org.continuity.workload.dsl.annotation.PropertyOverrideKey;
import org.continuity.workload.dsl.annotation.PropertyOverrideKey.Any;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * @author Henning Schulz
 *
 */
public class PropertyOverrideDeserializer extends JsonDeserializer<PropertyOverride<?>> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PropertyOverride<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		p.getText(); // Start token
		p.nextToken();
		String key = p.getText();
		p.nextToken();
		String value = p.getText();
		p.nextToken();
		p.getText(); // End token

		return createOverride(key, value);
	}

	@SuppressWarnings("unchecked")
	private <T> PropertyOverride<T> createOverride(String key, String value) {
		PropertyOverride<T> override = new PropertyOverride<>();
		override.setKey((Any<T>) PropertyOverrideKey.fromPrintableString(key));
		override.setValue(value);
		return override;
	}

}
