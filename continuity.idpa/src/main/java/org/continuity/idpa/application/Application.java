/**
 */
package org.continuity.idpa.application;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.continuity.idpa.AbstractIdpaElement;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents an application consisting of endpoints that can be called.
 *
 * @author Henning Schulz
 *
 */
@JsonPropertyOrder({ "version", "timestamp", "endpoints" })
public class Application extends AbstractIdpaElement {

	private String version;
	
	/**
	 * Default value is the date when the object was created.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH-mm-ss-SSSX")
	private Date timestamp = new Date();

	private List<Endpoint<?>> endpoints;

	/**
	 * Returns the endpoint representations of the represented system.
	 *
	 * @return the endpoint representations of the represented system
	 */
	public List<Endpoint<?>> getEndpoints() {
		if (endpoints == null) {
			endpoints = new ArrayList<>();
		}
		return endpoints;
	}

	/**
	 * Sets the endpoint representations of the represented system.
	 *
	 * @param endpoints
	 *            The endpoint representations of the represented system.
	 */
	public void setEndpoints(List<Endpoint<?>> endpoints) {
		this.endpoints = endpoints;
	}

	/**
	 * Gets the date at which the system is represented.
	 *
	 * @return The timestamp.
	 */
	public Date getTimestamp() {
		return this.timestamp;
	}

	/**
	 * Sets the date at which the system is represented.
	 *
	 * @param timestamp
	 *            The timestamp.
	 */
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	
	/**
	 * Gets the version of the system.
	 * 
	 * @return The version.
	 */
	public String getVersion() {
		return version;
	}
	
	/**
	 * Sets the version of the system.
	 * 
	 * @param version
	 * 				The version.
	 */
	public void setVersion(String version) {
		this.version = version;
	}

	public void addEndpoint(Endpoint<?> endpoint) {
		getEndpoints().add(endpoint);
	}

	@Override
	public String toString() {
		StringBuffer result = new StringBuffer(super.toString());
		result.append(" (id: ");
		result.append(getId());
		result.append(')');
		return result.toString();
	}

}
