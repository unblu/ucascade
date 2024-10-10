package com.unblu.ucascade.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

import controller.model.MergeRequestSimple;

public class GitlabMockUtil {

	public static final long PROJECT_ID = 1L;
	public static final long USER_ID = 1L;

	public static final String MR_EVENT_SOURCE_BRANCH = "some-feature";
	public static final String MR_EVENT_TARGET_BRANCH = "release/6.x.x";
	public static final String MR_EVENT_MERGE_COMMIT_SHA = "e819d39ed37c6d4b8e700b9e7f34c74c099c163b";
	public static final String MR_EVENT_MERGE_ACTION = "merge";
	public static final String MR_EVENT_CLOSE_ACTION = "close";

	public static final String DEFAULT_TARGET_BRANCH = "main";

	public static final String GITLAB_EVENT_UUID = "test-1234";

	public static MergeRequestSimple createDefaultMREvent() {
		return new MergeRequestSimple(PROJECT_ID, 100L, USER_ID, MR_EVENT_SOURCE_BRANCH, MR_EVENT_TARGET_BRANCH, "merged", MR_EVENT_MERGE_COMMIT_SHA, MR_EVENT_MERGE_ACTION, GITLAB_EVENT_UUID);
	}

	/**
	 * Gitlab API actions
	 */
	public static enum GitlabAction {
		// REST API
		CREATE_BRANCH, CREATE_MR, ACCEPT_MR, APPROVE_MR, GET_APPROVALS, UPDATE_MR, GET_OPEN_MRS, GET_MR, GET_BRANCH, GET_FILE, LIST_MR_PIPELINES, COMPARE_DIFF_BRANCHES, COMPARE_NO_DIFF_BRANCHES, GET_USER,
		// Webhook
		EVENT_MR_MERGED, EVENT_MR_FF_MERGED;
	}

	private static final EnumMap<GitlabAction, String> jsonTemplatesLocation = initJsonTemplatesLocationMap();

	/**
	 * Loads the JSON file content for a given action, possibly customizing some properties.
	 *
	 * @param action The action for which the corresponding JSON response is required.
	 * @param customProperties Map between the name and value of the properties to be modified using the new value.
	 * @return String containing the contents of the JSON file for the given action, with its properties customized accordingly to the provided map.
	 */
	public static String get(GitlabAction action, Map<String, Object> customProperties) {
		String origJson = ResourcesUtil.readFromResources(jsonTemplatesLocation.get(action));
		if (customProperties != null) {
			for (Entry<String, Object> customProperty : customProperties.entrySet()) {
				String name = customProperty.getKey();
				Object value = customProperty.getValue();
				origJson = origJson.replaceAll("(\\s*\"" + name + "\"\\s*:\\s*).+,", "$1" + value + ",");
			}
		}
		return origJson;
	}

	private static EnumMap<GitlabAction, String> initJsonTemplatesLocationMap() {
		EnumMap<GitlabAction, String> templates = new EnumMap<>(GitlabAction.class);
		templates.put(GitlabAction.CREATE_BRANCH, "/gitlab_template_json/api/createBranchResponse.json");
		templates.put(GitlabAction.CREATE_MR, "/gitlab_template_json/api/createMRResponse.json");
		templates.put(GitlabAction.ACCEPT_MR, "/gitlab_template_json/api/acceptMRResponse.json");
		templates.put(GitlabAction.APPROVE_MR, "/gitlab_template_json/api/approveMRResponse.json");
		templates.put(GitlabAction.GET_APPROVALS, "/gitlab_template_json/api/getMRApprovalsResponse.json");
		templates.put(GitlabAction.UPDATE_MR, "/gitlab_template_json/api/updateMRResponse.json");
		templates.put(GitlabAction.GET_OPEN_MRS, "/gitlab_template_json/api/getOpenMRsResponse.json");
		templates.put(GitlabAction.GET_MR, "/gitlab_template_json/api/getMRResponse.json");
		templates.put(GitlabAction.GET_BRANCH, "/gitlab_template_json/api/getBranchResponse.json");
		templates.put(GitlabAction.GET_FILE, "/gitlab_template_json/api/getFileFromRepository.json");
		templates.put(GitlabAction.LIST_MR_PIPELINES, "/gitlab_template_json/api/listMRPipelines.json");
		templates.put(GitlabAction.COMPARE_DIFF_BRANCHES, "/gitlab_template_json/api/compareDiffBranchesResponse.json");
		templates.put(GitlabAction.COMPARE_NO_DIFF_BRANCHES, "/gitlab_template_json/api/compareNoDiffBranchesResponse.json");
		templates.put(GitlabAction.GET_USER, "/gitlab_template_json/api/getUserResponse.json");
		templates.put(GitlabAction.EVENT_MR_MERGED, "/gitlab_template_json/webhook/mergedMREvent.json");
		templates.put(GitlabAction.EVENT_MR_FF_MERGED, "/gitlab_template_json/webhook/mergedFFMREvent.json");

		return templates;
	}
}
