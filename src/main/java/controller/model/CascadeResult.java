package controller.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CascadeResult {

	@JsonProperty("build_commit")
	private String buildCommit;

	@JsonProperty("build_timestamp")
	private String buildTimestamp;

	@JsonProperty("gitlab_event_uuid")
	private String gitlabEventUUID;

	@JsonProperty("error")
	private String error;

	@JsonProperty("previous_auto_mr_merged")
	private MergeRequestResult previousAutoMrMerged;

	@JsonProperty("previous_auto_mr_merged_error")
	private String previousAutoMrMergedError;

	@JsonProperty("created_auto_mr")
	private MergeRequestResult createdAutoMr;

	@JsonProperty("created_auto_mr_error")
	private String createdAutoMrError;

	@JsonProperty("existing_branch_deleted")
	private DeleteBranchResult existingBranchDelete;

	@JsonProperty("existing_branch_deleted_error")
	private String existingBranchDeleteError;

	public String getBuildCommit() {
		return buildCommit;
	}

	public void setBuildCommit(String buildCommit) {
		this.buildCommit = buildCommit;
	}

	public String getBuildTimestamp() {
		return buildTimestamp;
	}

	public void setBuildTimestamp(String buildTimestamp) {
		this.buildTimestamp = buildTimestamp;
	}

	public String getGitlabEventUUID() {
		return gitlabEventUUID;
	}

	public void setGitlabEventUUID(String gitlabEventUUID) {
		this.gitlabEventUUID = gitlabEventUUID;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public MergeRequestResult getPreviousAutoMrMerged() {
		return previousAutoMrMerged;
	}

	public void setPreviousAutoMrMerged(MergeRequestResult previousAutoMrMerged) {
		this.previousAutoMrMerged = previousAutoMrMerged;
	}

	public String getPreviousAutoMrMergedError() {
		return previousAutoMrMergedError;
	}

	public void setPreviousAutoMrMergedError(String previousAutoMrMergedError) {
		this.previousAutoMrMergedError = previousAutoMrMergedError;
	}

	public MergeRequestResult getCreatedAutoMr() {
		return createdAutoMr;
	}

	public void setCreatedAutoMr(MergeRequestResult createdAutoMr) {
		this.createdAutoMr = createdAutoMr;
	}

	public String getCreatedAutoMrError() {
		return createdAutoMrError;
	}

	public void setCreatedAutoMrError(String createdAutoMrError) {
		this.createdAutoMrError = createdAutoMrError;
	}

	public DeleteBranchResult getExistingBranchDelete() {
		return existingBranchDelete;
	}

	public void setExistingBranchDelete(DeleteBranchResult existingBranchDelete) {
		this.existingBranchDelete = existingBranchDelete;
	}

	public String getExistingBranchDeleteError() {
		return existingBranchDeleteError;
	}

	public void setExistingBranchDeleteError(String existingBranchDeleteError) {
		this.existingBranchDeleteError = existingBranchDeleteError;
	}

	@Override
	public int hashCode() {
		return Objects.hash(buildCommit, buildTimestamp, createdAutoMr, createdAutoMrError, error, existingBranchDelete, existingBranchDeleteError, gitlabEventUUID, previousAutoMrMerged, previousAutoMrMergedError);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CascadeResult other = (CascadeResult) obj;
		return Objects.equals(buildCommit, other.buildCommit) &&
				Objects.equals(buildTimestamp, other.buildTimestamp) &&
				Objects.equals(createdAutoMr, other.createdAutoMr) &&
				Objects.equals(createdAutoMrError, other.createdAutoMrError) &&
				Objects.equals(error, other.error) &&
				Objects.equals(existingBranchDelete, other.existingBranchDelete) &&
				Objects.equals(existingBranchDeleteError, other.existingBranchDeleteError) &&
				Objects.equals(gitlabEventUUID, other.gitlabEventUUID) &&
				Objects.equals(previousAutoMrMerged, other.previousAutoMrMerged) &&
				Objects.equals(previousAutoMrMergedError, other.previousAutoMrMergedError);
	}

	@Override
	public String toString() {
		return "CascadeResult [buildCommit=" + buildCommit + ", buildTimestamp=" + buildTimestamp + ", gitlabEventUUID=" + gitlabEventUUID + ", error=" + error + ", previousAutoMrMerged=" + previousAutoMrMerged + ", previousAutoMrMergedError=" + previousAutoMrMergedError + ", createdAutoMr=" + createdAutoMr + ", createdAutoMrError=" + createdAutoMrError + ", existingBranchDelete=" + existingBranchDelete + ", existingBranchDeleteError=" + existingBranchDeleteError + "]";
	}

}
