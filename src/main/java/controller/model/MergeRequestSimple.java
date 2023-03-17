package controller.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MergeRequestSimple {

	@JsonProperty("project_id")
	private Long projectId;
	@JsonProperty("mr_number")
	private Long mrNumber;
	@JsonProperty("user_id")
	private Long userId;
	@JsonProperty("source_branch")
	private String sourceBranch;
	@JsonProperty("target_branch")
	private String targetBranch;
	@JsonProperty("mr_state")
	private String mrState;
	@JsonProperty("merge_commit_sha")
	private String mergeCommitSha;
	@JsonProperty("mr_action")
	private String mrAction;

	@JsonProperty("gitlab_event_uuid")
	private String gitlabEventUUID;

	public MergeRequestSimple() {
		super();
	}

	public MergeRequestSimple(Long projectId, Long mrNumber, Long userId, String sourceBranch, String targetBranch, String mrState, String mergeCommitSha, String mrAction, String gitlabEventUUID) {
		this.projectId = projectId;
		this.mrNumber = mrNumber;
		this.userId = userId;
		this.sourceBranch = sourceBranch;
		this.targetBranch = targetBranch;
		this.mrState = mrState;
		this.mergeCommitSha = mergeCommitSha;
		this.gitlabEventUUID = gitlabEventUUID;
		this.mrAction = mrAction;
	}

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public Long getMrNumber() {
		return mrNumber;
	}

	public void setMrNumber(Long mrNumber) {
		this.mrNumber = mrNumber;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getSourceBranch() {
		return sourceBranch;
	}

	public void setSourceBranch(String sourceBranch) {
		this.sourceBranch = sourceBranch;
	}

	public String getTargetBranch() {
		return targetBranch;
	}

	public void setTargetBranch(String targetBranch) {
		this.targetBranch = targetBranch;
	}

	public String getMrState() {
		return mrState;
	}

	public void setMrState(String mrState) {
		this.mrState = mrState;
	}

	public String getMergeCommitSha() {
		return mergeCommitSha;
	}

	public void setMergeCommitSha(String mergeCommitSha) {
		this.mergeCommitSha = mergeCommitSha;
	}

	public String getMrAction() {
		return mrAction;
	}

	public void setMrAction(String mrAction) {
		this.mrAction = mrAction;
	}

	public String getGitlabEventUUID() {
		return gitlabEventUUID;
	}

	public void setGitlabEventUUID(String gitlabEventUUID) {
		this.gitlabEventUUID = gitlabEventUUID;
	}

	@Override
	public int hashCode() {
		return Objects.hash(gitlabEventUUID, mergeCommitSha, mrAction, mrNumber, mrState, projectId, sourceBranch, targetBranch, userId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MergeRequestSimple other = (MergeRequestSimple) obj;
		return Objects.equals(gitlabEventUUID, other.gitlabEventUUID) &&
				Objects.equals(mergeCommitSha, other.mergeCommitSha) &&
				Objects.equals(mrAction, other.mrAction) &&
				Objects.equals(mrNumber, other.mrNumber) &&
				Objects.equals(mrState, other.mrState) &&
				Objects.equals(projectId, other.projectId) &&
				Objects.equals(sourceBranch, other.sourceBranch) &&
				Objects.equals(targetBranch, other.targetBranch) &&
				Objects.equals(userId, other.userId);
	}

	@Override
	public String toString() {
		return "MergeRequestSimple [projectId=" + projectId + ", mrNumber=" + mrNumber + ", userId=" + userId + ", sourceBranch=" + sourceBranch + ", targetBranch=" + targetBranch + ", mrState=" + mrState + ", mergeCommitSha=" + mergeCommitSha + ", mrAction=" + mrAction + ", gitlabEventUUID=" + gitlabEventUUID + "]";
	}

}
