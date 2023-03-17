package controller.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConfigBranches {

	@JsonProperty("sourceBranchPattern")
	private String sourceBranchPattern;

	@JsonProperty("targetBranch")
	private String targetBranch;

	public String getSourceBranchPattern() {
		return sourceBranchPattern;
	}

	public void setSourceBranchPattern(String sourceBranchPattern) {
		this.sourceBranchPattern = sourceBranchPattern;
	}

	public ConfigBranches withSourceBranchPattern(String value) {
		setSourceBranchPattern(value);
		return this;
	}

	public String getTargetBranch() {
		return targetBranch;
	}

	public void setTargetBranch(String targetBranch) {
		this.targetBranch = targetBranch;
	}

	public ConfigBranches withTargetBranch(String value) {
		setTargetBranch(value);
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(sourceBranchPattern, targetBranch);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConfigBranches other = (ConfigBranches) obj;
		return Objects.equals(sourceBranchPattern, other.sourceBranchPattern) &&
				Objects.equals(targetBranch, other.targetBranch);
	}
}
