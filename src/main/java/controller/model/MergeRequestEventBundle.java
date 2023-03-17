package controller.model;

import org.gitlab4j.api.webhook.MergeRequestEvent;

public class MergeRequestEventBundle {

	private MergeRequestEvent mrEvent;
	private String gitlabEventUUID;

	public MergeRequestEventBundle(MergeRequestEvent mrEvent, String gitlabEventUUID) {
		this.mrEvent = mrEvent;
		this.gitlabEventUUID = gitlabEventUUID;
	}

	public MergeRequestEvent getMrEvent() {
		return mrEvent;
	}

	public String getGitlabEventUUID() {
		return gitlabEventUUID;
	}

}
