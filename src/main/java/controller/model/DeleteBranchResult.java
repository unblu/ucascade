/*
 *    Copyright 2008-2022 unblu inc., Sarnen, Switzerland
 *    All rights reserved.
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package controller.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteBranchResult {

	@JsonProperty("branch_name")
	private String branchName;

	public DeleteBranchResult() {
	}

	public DeleteBranchResult(String branchName) {
		this.branchName = branchName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(branchName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DeleteBranchResult other = (DeleteBranchResult) obj;
		return Objects.equals(branchName, other.branchName);
	}

	@Override
	public String toString() {
		return "DeleteBranchResult [branchName=" + branchName + "]";
	}
}
