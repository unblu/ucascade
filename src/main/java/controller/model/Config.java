package controller.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Config {

	@JsonProperty("branches")
	private List<ConfigBranches> branches;

	public List<ConfigBranches> getBranches() {
		return branches;
	}

	public void setBranches(List<ConfigBranches> branches) {
		this.branches = branches;
	}

	public Config addBranch(ConfigBranches value) {
		if (branches == null) {
			branches = new ArrayList<>();
		}
		branches.add(value);
		return this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(branches);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Config other = (Config) obj;
		return Objects.equals(branches, other.branches);
	}
}
