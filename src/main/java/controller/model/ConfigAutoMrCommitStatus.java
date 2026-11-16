package controller.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Opt-in: a commit status that ucascade sets to {@code success} on the commit of each automatic merge request it creates.
 */
public class ConfigAutoMrCommitStatus {

	public static final String DEFAULT_NAME = "ucascade";

	@JsonProperty("name")
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ConfigAutoMrCommitStatus withName(String value) {
		setName(value);
		return this;
	}

	/**
	 * @return the name of the commit status: the configured one, or {@value #DEFAULT_NAME}
	 */
	public String getNameOrDefault() {
		return (name == null || name.isBlank()) ? DEFAULT_NAME : name;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConfigAutoMrCommitStatus other = (ConfigAutoMrCommitStatus) obj;
		return Objects.equals(name, other.name);
	}
}
