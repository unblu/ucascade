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

import org.gitlab4j.api.models.MergeRequest;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MergeRequestResult {

	@JsonProperty("id")
	private Long id;

	@JsonProperty("project_id")
	private Long projectId;

	@JsonProperty("mr_number")
	private Long iid;

	@JsonProperty("assignee_id")
	private Long assigneeId;

	@JsonProperty("title")
	private String title;

	@JsonProperty("description")
	private String description;

	@JsonProperty("state")
	private String state;

	@JsonProperty("detailed_merge_status")
	private String detailedMergeStatus;

	@JsonProperty("has_conflicts")
	private Boolean hasConflicts;

	@JsonProperty("source_branch")
	private String sourceBranch;

	@JsonProperty("target_branch")
	private String targetBranch;

	@JsonProperty("web_url")
	private String webUrl;

	@JsonProperty("ucascade_state")
	private MergeRequestUcascadeState ucascadeState;

	public MergeRequestResult(MergeRequest mr) {
		id = mr.getIid();
		iid = mr.getIid();
		projectId = mr.getProjectId();
		title = mr.getTitle();
		description = mr.getDescription();
		state = mr.getState();
		detailedMergeStatus = mr.getDetailedMergeStatus();
		hasConflicts = mr.getHasConflicts();
		sourceBranch = mr.getSourceBranch();
		targetBranch = mr.getTargetBranch();
		webUrl = mr.getWebUrl();
		assigneeId = mr.getAssignee() == null ? null : mr.getAssignee().getId();
	}

	public MergeRequestResult() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getIid() {
		return iid;
	}

	public void setIid(Long iid) {
		this.iid = iid;
	}

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public Long getAssigneeId() {
		return assigneeId;
	}

	public void setAssigneeId(Long assigneeId) {
		this.assigneeId = assigneeId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getDetailedMergeStatus() {
		return detailedMergeStatus;
	}

	public void setDetailedMergeStatus(String detailedMergeStatus) {
		this.detailedMergeStatus = detailedMergeStatus;
	}

	public Boolean getHasConflicts() {
		return hasConflicts;
	}

	public void setHasConflicts(Boolean hasConflicts) {
		this.hasConflicts = hasConflicts;
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

	public String getWebUrl() {
		return webUrl;
	}

	public void setWebUrl(String webUrl) {
		this.webUrl = webUrl;
	}

	public MergeRequestUcascadeState getUcascadeState() {
		return ucascadeState;
	}

	public void setUcascadeState(MergeRequestUcascadeState ucascadeState) {
		this.ucascadeState = ucascadeState;
	}

	@Override
	public int hashCode() {
		return Objects.hash(assigneeId, description, hasConflicts, id, iid, detailedMergeStatus, projectId, sourceBranch, state, targetBranch, title, ucascadeState, webUrl);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MergeRequestResult other = (MergeRequestResult) obj;
		return Objects.equals(assigneeId, other.assigneeId) &&
				Objects.equals(description, other.description) &&
				Objects.equals(hasConflicts, other.hasConflicts) &&
				Objects.equals(id, other.id) &&
				Objects.equals(iid, other.iid) &&
				Objects.equals(detailedMergeStatus, other.detailedMergeStatus) &&
				Objects.equals(projectId, other.projectId) &&
				Objects.equals(sourceBranch, other.sourceBranch) &&
				Objects.equals(state, other.state) &&
				Objects.equals(targetBranch, other.targetBranch) &&
				Objects.equals(title, other.title) &&
				ucascadeState == other.ucascadeState &&
				Objects.equals(webUrl, other.webUrl);
	}

	@Override
	public String toString() {
		return "MergeRequestResult [id=" + id + ", projectId=" + projectId + ", iid=" + iid + ", assigneeId=" + assigneeId + ", title=" + title + ", description=" + description + ", state=" + state + ", mergeStatus=" + detailedMergeStatus + ", hasConflicts=" + hasConflicts + ", sourceBranch=" + sourceBranch + ", targetBranch=" + targetBranch + ", webUrl=" + webUrl + ", ucascadeState=" + ucascadeState + "]";
	}
}
