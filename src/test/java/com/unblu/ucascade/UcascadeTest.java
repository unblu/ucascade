package com.unblu.ucascade;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.utils.JacksonJson;
import org.gitlab4j.api.utils.UrlEncoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.unblu.ucascade.util.GitlabMockUtil;
import com.unblu.ucascade.util.GitlabMockUtil.GitlabAction;

import controller.model.MergeRequestSimple;
import controller.model.MergeRequestUcascadeState;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import service.GitLabService;

@QuarkusTest
@QuarkusTestResource(WireMockGitlabProxy.class)
class UcascadeTest {

	final static String API_PREFIX = "/api/v4/";
	final static String API_AUTH_KEY_NAME = "PRIVATE-TOKEN";

	@InjectWireMock
	WireMockServer wireMockServer;

	@ConfigProperty(name = "gitlab.api.token")
	String apiToken;
	@ConfigProperty(name = "gitlab.api.token.approver")
	Optional<String> apiTokenApprover;

	String branchModelContent;

	@BeforeEach
	void init() throws IOException {
		wireMockServer.resetAll();
		branchModelContent = Files.readString(Path.of("src/test/resources/ucascade.json"));
		setupGetUserStub();
	}

	@Test
	void testEndpointRapidReturn() throws Exception {
		setupDefaultStubs();

		given().when()
				.header("Content-Type", "application/json")
				.header("X-Gitlab-Event-UUID", GitlabMockUtil.GITLAB_EVENT_UUID)
				.body(GitlabMockUtil.get(GitlabAction.EVENT_MR_MERGED, null))
				.post("/ucascade/merge-request")
				.then()
				.statusCode(Response.Status.ACCEPTED.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("build_commit", equalTo("6af21ad"))
				.body("build_timestamp", equalTo("2022-01-01T07:21:58.378413Z"));

		verifyRequests(12);
	}

	@Test
	void testEndpointBlocking() throws Exception {
		// corresponds to the merge event of MR !100 feature branch targeting branch 'release/6.x.x'
		// input is the GitLab event JSON
		// expect to create an auto-merge request: !101
		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;
		String expectedTitle = "[ucascade-TEST] Auto MR: release/6.x.x -> " + nextMainBranch + " (!100)";
		String expectedDescription = "Automatic cascade merge request: " + "`something` !" + (mrNumber - 1) + " --> `release/6.x.x` --> `main`";

		setupDefaultStubs();

		given().when()
				.header("Content-Type", "application/json")
				.header("X-Gitlab-Event-UUID", GitlabMockUtil.GITLAB_EVENT_UUID)
				.body(GitlabMockUtil.get(GitlabAction.EVENT_MR_MERGED, null))
				.post("/ucascade/merge-request-blocking")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("build_commit", equalTo("6af21ad"))
				.body("build_timestamp", equalTo("2022-01-01T07:21:58.378413Z"))
				.body("created_auto_mr.title", equalTo(expectedTitle))
				.body("created_auto_mr.description", equalTo(expectedDescription))
				.body("created_auto_mr.mr_number", equalTo(mrNumber.intValue()))
				.body("created_auto_mr.source_branch", equalTo("mr100_release/6.x.x"))
				.body("created_auto_mr.target_branch", equalTo(nextMainBranch))
				.body("created_auto_mr.ucascade_state", equalTo(MergeRequestUcascadeState.MERGED.name()))
				.body("existing_branch_deleted", nullValue());

		verifyRequests(12);
	}

	@Test
	void testSuccessCaseMergeEvent() throws Exception {
		// corresponds to the merge event of MR !100 feature branch targeting branch 'release/6.x.x'
		// input is using the replay endpoint
		// expect to create an auto-merge request: !101
		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;
		String expectedTitle = "[ucascade-TEST] Auto MR: release/6.x.x -> " + nextMainBranch + " (!100)";
		String expectedDescription = "Automatic cascade merge request: " + "`something` !" + (mrNumber - 1) + " --> `release/6.x.x` --> `main`";

		setupDefaultStubs();
		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr.title", equalTo(expectedTitle))
				.body("created_auto_mr.description", equalTo(expectedDescription))
				.body("created_auto_mr.mr_number", equalTo(mrNumber.intValue()))
				.body("created_auto_mr.source_branch", equalTo("mr100_release/6.x.x"))
				.body("created_auto_mr.target_branch", equalTo(nextMainBranch))
				.body("created_auto_mr.ucascade_state", equalTo(MergeRequestUcascadeState.MERGED.name()))
				.body("existing_branch_deleted", nullValue());

		verifyRequests(12);
	}

	@Test
	void testSuccessCaseCloseEvent() throws Exception {
		// corresponds to the close event of MR !100 automatically created branch targeting branch 'main'
		// input is using the replay endpoint
		// expect to delete the source branch of MR !100 and to merge existing MRs between the same source/target branches
		Long mrNumber = 100L;
		String branchToDelete = "mr99_release/6.x.x";

		//		setupDefaultStubs();
		setupDeleteBranchStubOk(GitlabMockUtil.PROJECT_ID, branchToDelete);
		MergeRequestSimple mrSimple = new MergeRequestSimple(GitlabMockUtil.PROJECT_ID, mrNumber, GitlabMockUtil.USER_ID, branchToDelete, GitlabMockUtil.DEFAULT_TARGET_BRANCH, "closed", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, GitlabMockUtil.MR_EVENT_CLOSE_ACTION, GitlabMockUtil.GITLAB_EVENT_UUID);

		Long existingOpenMrNr = 90L;
		setupGetOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		setupGetMRPipelinesRequestStub(GitlabMockUtil.PROJECT_ID, existingOpenMrNr, true);
		setupAcceptMergeRequestStub(GitlabMockUtil.PROJECT_ID, existingOpenMrNr, Map.of("iid", "" + existingOpenMrNr), true, true);

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged.mr_number", equalTo(existingOpenMrNr.intValue()))
				.body("previous_auto_mr_merged.ucascade_state", equalTo(MergeRequestUcascadeState.MERGED.name()))
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", equalTo(branchToDelete));

		verifyRequests(4);
	}

	private void setupDefaultStubs() {
		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;

		setupCreateBranchStub(GitlabMockUtil.PROJECT_ID, "mr100_release/6.x.x", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, null);
		setupGetBranchStub(GitlabMockUtil.PROJECT_ID, nextMainBranch, null);
		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		Map<String, Object> customProperties = Map.of("merge_status", "\"checking\"");
		setupCreateMergeRequestStub(GitlabMockUtil.PROJECT_ID, customProperties);
		setupAcceptMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null, true, true);
		setupApproveMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		customProperties = Map.of("source_branch", "\"something\"");
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, (mrNumber - 1), customProperties);
		setupGetMRPipelinesRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, true);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupCompareBranchesRequestStub(GitlabMockUtil.PROJECT_ID, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, true);
	}

	@Test
	void testSuccessCaseNoPipelineConfigured() throws Exception {
		// when no pipelines are configured for this MR,
		// the merge_when_pipeline_succeeds flag must be set to false.
		// expect to create an auto-merge request: !101 and to merge it directly without waiting for pipelines
		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;
		String expectedTitle = "[ucascade-TEST] Auto MR: release/6.x.x -> " + nextMainBranch + " (!100)";

		setupCreateBranchStub(GitlabMockUtil.PROJECT_ID, "mr100_release/6.x.x", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, null);
		setupGetBranchStub(GitlabMockUtil.PROJECT_ID, nextMainBranch, null);
		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		Map<String, Object> customProperties = Map.of("merge_status", "\"checking\"");
		setupCreateMergeRequestStub(GitlabMockUtil.PROJECT_ID, customProperties);
		setupAcceptMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null, false, true);
		setupApproveMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		customProperties = Map.of("source_branch", "\"something\"");
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, (mrNumber - 1), customProperties);
		setupGetMRPipelinesRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, false);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupCompareBranchesRequestStub(GitlabMockUtil.PROJECT_ID, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, true);
		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr.title", equalTo(expectedTitle))
				.body("created_auto_mr.mr_number", equalTo(mrNumber.intValue()))
				.body("created_auto_mr.source_branch", equalTo("mr100_release/6.x.x"))
				.body("created_auto_mr.target_branch", equalTo(nextMainBranch))
				.body("created_auto_mr.ucascade_state", equalTo(MergeRequestUcascadeState.MERGED.name()))
				.body("existing_branch_deleted", nullValue());

		verifyRequests(12);
	}

	@Test
	void testFollowUpCascade() throws Exception {
		// corresponds to the merge event of auto merge-request !100 from the 'mr99_release/5.x.x' branch targeting branch 'release/6.x.x'
		// expect to create an auto-merge request: !101

		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;
		String expectedTitle = "[ucascade-TEST] Auto MR: release/6.x.x -> " + nextMainBranch + " (!100)";

		Long projectId = 2345L;
		setupCreateBranchStub(projectId, "mr100_release/6.x.x", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, null);
		setupGetBranchStub(projectId, nextMainBranch, null);
		setupGetEmptyOpenMergeRequestStub(projectId);
		setupDeleteBranchStubNotFound(projectId, "mr99_release/5.x.x");
		Map<String, Object> projectIdProperties = Map.of("project_id", "" + projectId);
		Map<String, Object> customProperties = Map.of("merge_status", "\"checking\"", "project_id", "" + projectId);
		setupCreateMergeRequestStub(projectId, customProperties);
		setupAcceptMergeRequestStub(projectId, mrNumber, projectIdProperties, true, true);
		setupApproveMergeRequestStub(projectId, mrNumber, projectIdProperties);
		setupGetMergeRequestStub(projectId, mrNumber, projectIdProperties);
		customProperties = Map.of("project_id", "" + projectId, "source_branch", "\"something\"");
		setupGetMergeRequestStub(projectId, (mrNumber - 1), customProperties);
		setupGetMRPipelinesRequestStub(projectId, mrNumber, true);
		setupGetFileFromRepositoryRequestStub(projectId, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupCompareBranchesRequestStub(projectId, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, true);

		MergeRequestSimple mrSimple = new MergeRequestSimple(projectId, 100L, GitlabMockUtil.USER_ID, "mr99_release/5.x.x", "release/6.x.x", "merged", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, GitlabMockUtil.MR_EVENT_MERGE_ACTION, "event-93484");

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo("event-93484"))
				.body("created_auto_mr.title", equalTo(expectedTitle))
				.body("created_auto_mr.project_id", equalTo(projectId.intValue()))
				.body("created_auto_mr.mr_number", equalTo(mrNumber.intValue()))
				.body("created_auto_mr.source_branch", equalTo("mr100_release/6.x.x"))
				.body("created_auto_mr.target_branch", equalTo(nextMainBranch))
				.body("created_auto_mr.ucascade_state", equalTo(MergeRequestUcascadeState.MERGED.name()))
				.body("existing_branch_deleted.branch_name", nullValue());

		verifyRequests(13);
	}

	@Test
	void testAlreadyExistingMR() throws Exception {
		// corresponds to the merge event of MR !100 feature branch targeting branch 'release/6.x.x'
		// there is already an auto merge-request between 'release/6.x.x' and 'main'
		// expect to create an auto-merge request: !101, do not merge it

		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;
		String expectedTitle = "[ucascade-TEST] Auto MR: release/6.x.x -> " + nextMainBranch + " (!100)";

		setupCreateBranchStub(GitlabMockUtil.PROJECT_ID, "mr100_release/6.x.x", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, null);
		setupGetBranchStub(GitlabMockUtil.PROJECT_ID, nextMainBranch, null);
		setupGetOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		Map<String, Object> customProperties = Map.of("merge_status", "\"checking\"");
		setupCreateMergeRequestStub(GitlabMockUtil.PROJECT_ID, customProperties);
		setupApproveMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		customProperties = Map.of("source_branch", "\"something\"");
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber - 1, customProperties);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupCompareBranchesRequestStub(GitlabMockUtil.PROJECT_ID, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, true);

		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr.title", equalTo(expectedTitle))
				.body("created_auto_mr.mr_number", equalTo(mrNumber.intValue()))
				.body("created_auto_mr.source_branch", equalTo("mr100_release/6.x.x"))
				.body("created_auto_mr.target_branch", equalTo(nextMainBranch))
				.body("created_auto_mr.merge_status", equalTo("checking"))
				.body("created_auto_mr.ucascade_state", equalTo(MergeRequestUcascadeState.NOT_MERGED_CONCURRENT_MRS.name()))
				.body("existing_branch_deleted", nullValue());

		verifyRequests(9);
	}

	@Test
	void testMRCannotBeMergedConflicts() throws Exception {
		// corresponds to the merge event of MR !100 feature branch targeting branch 'release/6.x.x'
		// expect to create an auto-merge request: !101, do not merge it because of the conflicts

		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;

		setupCreateBranchStub(GitlabMockUtil.PROJECT_ID, "mr100_release/6.x.x", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, null);
		setupGetBranchStub(GitlabMockUtil.PROJECT_ID, nextMainBranch, null);
		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		Map<String, Object> customProperties = Map.of("merge_status", "\"checking\"");
		setupCreateMergeRequestStub(GitlabMockUtil.PROJECT_ID, customProperties);
		setupApproveMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		Map<String, Object> mrCustomProperties = Map.of(
				"title", "\"[ucascade] Auto MR: release/6.x.x -> main (!100)\"",
				"merge_status", "\"cannot_be_merged\"",
				"has_conflicts", true);
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, mrCustomProperties);
		mrCustomProperties = Map.of(
				"iid", "100",
				"source_branch", "\"something\"");
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, 100L, mrCustomProperties);
		setupUpdateMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupCompareBranchesRequestStub(GitlabMockUtil.PROJECT_ID, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, true);

		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr.merge_status", equalTo("cannot_be_merged"))
				.body("created_auto_mr.has_conflicts", equalTo(true))
				.body("created_auto_mr.ucascade_state", equalTo(MergeRequestUcascadeState.NOT_MERGED_CONFLICTS.name()))
				.body("created_auto_mr.assignee_id", equalTo(987)) // the merge_user of the previous MR
				.body("existing_branch_deleted", nullValue());

		verifyRequests(12);
	}

	@Test
	void testMRCannotBeMergedUnknownReason() throws Exception {
		// corresponds to the merge event of MR !100 feature branch targeting branch 'release/6.x.x'
		// expect to create an auto-merge request: !101, do not merge it because of unknown reason/unrecognized merge_status

		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;

		setupCreateBranchStub(GitlabMockUtil.PROJECT_ID, "mr100_release/6.x.x", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, null);
		setupGetBranchStub(GitlabMockUtil.PROJECT_ID, nextMainBranch, null);
		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		Map<String, Object> customProperties = Map.of("merge_status", "\"checking\"");
		setupCreateMergeRequestStub(GitlabMockUtil.PROJECT_ID, customProperties);
		setupApproveMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		Map<String, Object> mrCustomProperties = Map.of(
				"title", "\"[ucascade] Auto MR: release/6.x.x -> main (!100)\"",
				"merge_status", "\"cannot_be_merged_recheck\"");
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, mrCustomProperties);
		mrCustomProperties = Map.of(
				"iid", "100");
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, 100L, mrCustomProperties);
		mrCustomProperties = Map.of(
				"merge_status", "\"cannot_be_merged_recheck\"",
				"has_conflicts", "false");
		setupUpdateMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, mrCustomProperties);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupCompareBranchesRequestStub(GitlabMockUtil.PROJECT_ID, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, true);

		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr.merge_status", equalTo("cannot_be_merged_recheck"))
				.body("created_auto_mr.has_conflicts", equalTo(false))
				.body("created_auto_mr.ucascade_state", equalTo(MergeRequestUcascadeState.NOT_MERGED_UNKNOWN_REASON.name()))
				.body("created_auto_mr.assignee_id", equalTo(987)) // the merge_user of the previous MR
				.body("existing_branch_deleted", nullValue());

		verifyRequests(12);
	}

	@Test
	void testDeleteBranch() throws Exception {
		// corresponds to the merge event of auto merge-request MR !102 (branch 'mr99_release/6.x.x' targeting 'main')
		// the branch 'mr99_release/6.x.x' is deleted.

		setupDeleteBranchStubOk(GitlabMockUtil.PROJECT_ID, "mr99_release/6.x.x");
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);

		MergeRequestSimple mrSimple = new MergeRequestSimple(GitlabMockUtil.PROJECT_ID, 102L, GitlabMockUtil.USER_ID, "mr99_release/6.x.x", "main", "merged", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, GitlabMockUtil.MR_EVENT_MERGE_ACTION, GitlabMockUtil.GITLAB_EVENT_UUID);

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", equalTo("mr99_release/6.x.x"));

		verifyRequests(3);
	}

	@Test
	void testMergePreviousAutoMergeRequest() throws Exception {
		// corresponds to the merge event of auto merge-request MR !110 (branch 'mr107_release/6.x.x' targeting 'main')
		// there is already an auto merge-request between 'release/6.x.x' and 'main'
		// expect to merge the oldest from this list

		Long mrNr = 90L;
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupGetOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		setupGetMRPipelinesRequestStub(GitlabMockUtil.PROJECT_ID, mrNr, true);
		setupAcceptMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNr, Map.of("iid", "" + mrNr), true, true);
		setupDeleteBranchStubNotFound(GitlabMockUtil.PROJECT_ID, "mr107_release/6.x.x");

		MergeRequestSimple mrSimple = new MergeRequestSimple(GitlabMockUtil.PROJECT_ID, 110L, GitlabMockUtil.USER_ID, "mr107_release/6.x.x", "main", "merged", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, GitlabMockUtil.MR_EVENT_MERGE_ACTION, "event7462");

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo("event7462"))
				.body("previous_auto_mr_merged.mr_number", equalTo(mrNr.intValue()))
				.body("previous_auto_mr_merged.ucascade_state", equalTo(MergeRequestUcascadeState.MERGED.name()))
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", nullValue());

		verifyRequests(5);
	}

	@Test
	void testMergeCorrectPreviousAutoMergeRequest() throws Exception {
		// corresponds to the merge event of auto merge-request MR !110 (branch 'mr107_release/6.x.x' targeting 'main')
		// there are already auto merge-requests between:
		//  - 'release/6.x.x' and 'main' (!101)
		//  - 'prod' and 'main' (!90)
		// expect to merge !101: the oldest between 'release/6.x.x' and 'main'.

		Long mrNr = 101L;
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		MergeRequest mr90 = createMr(90L, "mr56_prod", "main", false);
		MergeRequest mr101 = createMr(mrNr, "mr95_release/6.x.x", "main", false);
		List<MergeRequest> openMrs = List.of(mr90, mr101);
		String body = toJson(openMrs);
		setupGetOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID, body);
		setupGetMRPipelinesRequestStub(GitlabMockUtil.PROJECT_ID, mrNr, true);
		setupAcceptMergeRequestStub(GitlabMockUtil.PROJECT_ID, 101L, null, true, true);
		setupDeleteBranchStubNotFound(GitlabMockUtil.PROJECT_ID, "mr107_release/6.x.x");

		MergeRequestSimple mrSimple = new MergeRequestSimple(GitlabMockUtil.PROJECT_ID, 110L, GitlabMockUtil.USER_ID, "mr107_release/6.x.x", "main", "merged", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, GitlabMockUtil.MR_EVENT_MERGE_ACTION, "event945837");

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo("event945837"))
				.body("previous_auto_mr_merged.mr_number", equalTo(mrNr.intValue()))
				.body("previous_auto_mr_merged.ucascade_state", equalTo(MergeRequestUcascadeState.MERGED.name()))
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", nullValue());

		verifyRequests(5);
	}

	@Test
	void testDoNotMergePreviousAutoMergeRequest() throws Exception {
		// corresponds to the merge event of auto merge-request MR !110 (branch 'mr107_release/6.x.x' targeting 'main')
		// there are already auto merge-requests between 'release/6.x.x' and 'main': !101 and !90
		// MR !90 has already mergeWhenPipelineSucceeds == true
		// expect to not merge !90 or !101 (because the flag is already set for !90)

		Long mrNr = 101L;
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		MergeRequest mr90 = createMr(90L, "mr56_release/6.x.x", "main", true);
		MergeRequest mr101 = createMr(mrNr, "mr95_release/6.x.x", "main", false);
		List<MergeRequest> openMrs = List.of(mr90, mr101);
		String body = toJson(openMrs);
		setupGetOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID, body);
		setupDeleteBranchStubNotFound(GitlabMockUtil.PROJECT_ID, "mr107_release/6.x.x");

		MergeRequestSimple mrSimple = new MergeRequestSimple(GitlabMockUtil.PROJECT_ID, 110L, GitlabMockUtil.USER_ID, "mr107_release/6.x.x", "main", "merged", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, GitlabMockUtil.MR_EVENT_MERGE_ACTION, "event94857");

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo("event94857"))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", nullValue());

		verifyRequests(3);
	}

	@Test
	void testTargetBranchNonExistent() throws Exception {
		//error because the configured branch in 'ucascade.json' does not exist
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;

		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		setupGetBranchStubNotFound(GitlabMockUtil.PROJECT_ID, nextMainBranch);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);

		MergeRequestSimple mrSimple = new MergeRequestSimple(GitlabMockUtil.PROJECT_ID, 90L, GitlabMockUtil.USER_ID, GitlabMockUtil.MR_EVENT_SOURCE_BRANCH, GitlabMockUtil.MR_EVENT_TARGET_BRANCH, "merged", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, GitlabMockUtil.MR_EVENT_MERGE_ACTION, GitlabMockUtil.GITLAB_EVENT_UUID);

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("created_auto_mr_error", equalTo("GitlabEvent: '" + GitlabMockUtil.GITLAB_EVENT_UUID + "' | Branch named '" + nextMainBranch +
						"' does not exist in project '" + GitlabMockUtil.PROJECT_ID +
						"'. Please check the ucascade configuration file."));

		verifyRequests(3);
	}

	@Test
	void testMergeWithoutApprovals() throws Exception {
		// MR does not have all the required approvals, thus cannot be merged.
		Long mrNumber = 101L;
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;
		String sourceBranch = "mr100_release/6.x.x";

		setupCreateBranchStub(GitlabMockUtil.PROJECT_ID, sourceBranch, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, null);
		setupGetBranchStub(GitlabMockUtil.PROJECT_ID, nextMainBranch, null);
		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		Map<String, Object> customProperties = Map.of("merge_status", "\"checking\"");
		setupCreateMergeRequestStub(GitlabMockUtil.PROJECT_ID, customProperties);
		setupAcceptMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null, true, false);
		Map<String, Object> approvalsCustomProperties = Map.of(
				"approvals_required", "1",
				"approvals_left", "1");
		setupApproveMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, approvalsCustomProperties);
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, null);
		customProperties = Map.of("source_branch", "\"something\"");
		setupGetMergeRequestStub(GitlabMockUtil.PROJECT_ID, (mrNumber - 1), customProperties);
		setupGetMRPipelinesRequestStub(GitlabMockUtil.PROJECT_ID, mrNumber, true);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();
		setupCompareBranchesRequestStub(GitlabMockUtil.PROJECT_ID, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, true);

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("created_auto_mr_error", equalTo("GitlabEvent: '" + GitlabMockUtil.GITLAB_EVENT_UUID + "' | Cannot accept merge request '!" + mrNumber +
						"' from '" + sourceBranch + "' into '" + nextMainBranch + "' in project '" + GitlabMockUtil.PROJECT_ID + "'"));

		verifyRequests(210);
	}

	@Test
	void testNoDiffBetweenBranches() throws Exception {
		// expect no auto-merge request since there are no diff between source and target branches
		String nextMainBranch = GitlabMockUtil.DEFAULT_TARGET_BRANCH;

		setupGetBranchStub(GitlabMockUtil.PROJECT_ID, nextMainBranch, null);
		setupGetEmptyOpenMergeRequestStub(GitlabMockUtil.PROJECT_ID);
		setupGetFileFromRepositoryRequestStub(GitlabMockUtil.PROJECT_ID, "ucascade.json", GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA);
		setupCompareBranchesRequestStub(GitlabMockUtil.PROJECT_ID, GitlabMockUtil.MR_EVENT_MERGE_COMMIT_SHA, nextMainBranch, false);
		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", nullValue());

		verifyRequests(4);
	}

	@Test
	void testGetPrevMRFromBranchName() {
		Assertions.assertEquals(GitLabService.getPrevMergeRequestNumber("mr123_something"),
				123L);
		Assertions.assertEquals(GitLabService.getPrevMergeRequestNumber("mr123_something/1.x.x"),
				123L);
		Assertions.assertEquals(GitLabService.getPrevMergeRequestNumber("mr123_something/987"),
				123L);
		Assertions.assertEquals(GitLabService.getPrevMergeRequestNumber("mr123_something/mr987_else"),
				123L);
		Assertions.assertEquals(GitLabService.getPrevMergeRequestNumber("mr_unrelated"),
				null);
		Assertions.assertEquals(GitLabService.getPrevMergeRequestNumber("mr123unrelated"),
				null);
		Assertions.assertEquals(GitLabService.getPrevMergeRequestNumber("something_mr123_unrelated"),
				null);
	}

	@Test
	void testFormatCascadeElement() {
		Assertions.assertEquals("`release/6.x.x` !123 --> ",
				GitLabService.formatCascadeElement("-->", 123L, "mr123_release/6.x.x"));
		Assertions.assertEquals("`release/6.x.x` !123 --> ",
				GitLabService.formatCascadeElement("-->", 123L, "release/6.x.x"));
		Assertions.assertEquals("`release/6.x.x` !123 ",
				GitLabService.formatCascadeElement(null, 123L, "release/6.x.x"));
		Assertions.assertEquals("`release/6.x.x` --> ",
				GitLabService.formatCascadeElement("-->", null, "release/6.x.x"));
	}

	@Test
	void testEventIgnoredIfActionNotEqualsToMerge() throws Exception {
		// expect merge-request event to be ignored if action is not 'merge'
		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();
		mrSimple.setMrAction("update");

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", nullValue());

		verifyRequests(0);
	}

	@Test
	void testEventIgnoredIfStateNotEqualsToMerged() throws Exception {
		// expect merge-request event to be ignored if MR state is not 'merged'
		MergeRequestSimple mrSimple = GitlabMockUtil.createDefaultMREvent();
		mrSimple.setMrState("opened");

		given().when()
				.header("Content-Type", "application/json")
				.body(mrSimple)
				.post("/ucascade/replay")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo(GitlabMockUtil.GITLAB_EVENT_UUID))
				.body("previous_auto_mr_merged", nullValue())
				.body("created_auto_mr", nullValue())
				.body("existing_branch_deleted.branch_name", nullValue());

		verifyRequests(0);
	}

	@Test
	void testEndpointRapidReturnMalformedRequest() throws Exception {
		String json = """
				{
				    "foo" : "bar",
				    "baz" : 43
				}
				""";
		given().when()
				.header("Content-Type", "application/json")
				.body(json)
				.post("/ucascade/merge-request")
				.then()
				.statusCode(Response.Status.ACCEPTED.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", nullValue())
				.body("build_commit", equalTo("6af21ad"))
				.body("build_timestamp", equalTo("2022-01-01T07:21:58.378413Z"))
				.body("error", startsWith("Could not resolve subtype of"));

		verifyRequests(0);
	}

	@Test
	void testInvalidEndpoint() throws Exception {
		String json = """
				{
				    "foo" : "bar",
				    "baz" : 43
				}
				""";
		given().when()
				.header("Content-Type", "application/json")
				.header("X-Gitlab-Event-UUID", "test-1289369")
				.body(json)
				.post("/foo")
				.then()
				.statusCode(Response.Status.ACCEPTED.getStatusCode())
				.body(startsWith("{\n"))
				.body(endsWith("\n}"))
				.body("gitlab_event_uuid", equalTo("test-1289369"))
				.body("build_commit", equalTo("6af21ad"))
				.body("build_timestamp", equalTo("2022-01-01T07:21:58.378413Z"))
				.body("error", equalTo("Invalid path: /foo"));

		verifyRequests(0);
	}

	@Test
	void testHealthLive() throws Exception {
		//make sure we get an answer from the 'quarkus-smallrye-health' module
		given().when()
				.get("/q/health/live")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body("status", notNullValue())
				.body("checks", notNullValue())
				.body("gitlab_event_uuid", nullValue())
				.body("build_commit", nullValue())
				.body("build_timestamp", nullValue())
				.body("error", nullValue());

		verifyRequests(0);
	}

	@Test
	void testHealthReady() throws Exception {
		//make sure we get an answer from the 'quarkus-smallrye-health' module
		given().when()
				.post("/q/health/ready")
				.then()
				.statusCode(Response.Status.OK.getStatusCode())
				.body("status", notNullValue())
				.body("checks", notNullValue())
				.body("gitlab_event_uuid", nullValue())
				.body("build_commit", nullValue())
				.body("build_timestamp", nullValue())
				.body("error", nullValue());

		verifyRequests(0);
	}

	private void verifyRequests(int expectedRequestNumber) throws InterruptedException {
		List<ServeEvent> allServeEvents = waitForRequests(expectedRequestNumber);

		//If getUser() was called, expect one more call:
		int expected;
		if (allServeEvents.stream().anyMatch(e -> isGetUserStub(e.getStubMapping()))) {
			expected = expectedRequestNumber + 1;
		} else {
			expected = expectedRequestNumber;
		}
		Assertions.assertEquals(expected, allServeEvents.size(), "Number of requests to GitLab");

		//Verify that all stubs where called at least once. getUser() is defined as stub, but unused if the GitLabService was already initialized for a given test
		List<StubMapping> usedStubs = allServeEvents.stream().map(e -> e.getStubMapping()).toList();
		List<StubMapping> stubMappings = wireMockServer.getStubMappings();
		List<String> unused = stubMappings.stream()
				.filter(s -> !usedStubs.contains(s))
				.filter(s -> !isGetUserStub(s))
				.map(e -> e.getRequest().toString())
				.toList();
		if (!unused.isEmpty()) {
			Assertions.fail("Some defined stubs were not called by the GitLab client:\n" + unused);
		}
	}

	private List<ServeEvent> waitForRequests(int minimalNumberOfRequestsToWaitFor) throws InterruptedException {
		int countDown = 30;
		List<ServeEvent> allServeEvents = wireMockServer.getAllServeEvents();
		while (allServeEvents.size() < minimalNumberOfRequestsToWaitFor && countDown-- > 0) {
			TimeUnit.SECONDS.sleep(1);
			allServeEvents = wireMockServer.getAllServeEvents();
		}
		return allServeEvents;
	}

	private boolean isGetUserStub(StubMapping stub) {
		return Objects.equals("/api/v4/user", stub.getRequest().getUrlPath());
	}

	private void setupCreateBranchStub(Long projectId, String branch, String ref, Map<String, Object> customProperties) {
		wireMockServer.stubFor(
				post(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/repository/branches"))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.withQueryParam("branch", WireMock.equalTo(branch))
						.withQueryParam("ref", WireMock.equalTo(ref))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(GitlabMockUtil.get(GitlabAction.CREATE_BRANCH, customProperties))));
	}

	private void setupGetBranchStub(Long projectId, String branchName, Map<String, Object> customProperties) {
		try {
			wireMockServer.stubFor(
					get(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/repository/branches/" + UrlEncoder.urlEncode(branchName)))
							.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
							.willReturn(aResponse()
									.withHeader("Content-Type", "application/json")
									.withBody(GitlabMockUtil.get(GitlabAction.GET_BRANCH, customProperties))));
		} catch (GitLabApiException e) {
			e.printStackTrace();
		}
	}

	private void setupGetBranchStubNotFound(Long projectId, String branchName) {
		try {
			wireMockServer.stubFor(
					get(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/repository/branches/" + UrlEncoder.urlEncode(branchName)))
							.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
							.willReturn(aResponse()
									.withStatus(404)
									.withHeader("Content-Type", "application/json")
									.withBody("{\"message\":\"404 Branch Not Found\"}")));
		} catch (GitLabApiException e) {
			e.printStackTrace();
		}
	}

	private void setupDeleteBranchStubNotFound(Long projectId, String branchName) {
		try {
			wireMockServer.stubFor(
					delete(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/repository/branches/" + UrlEncoder.urlEncode(branchName)))
							.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
							.willReturn(aResponse()
									.withStatus(404)
									.withHeader("Content-Type", "application/json")
									.withBody("{\"message\":\"404 Branch Not Found\"}")));
		} catch (GitLabApiException e) {
			e.printStackTrace();
		}
	}

	private void setupDeleteBranchStubOk(Long projectId, String branchName) {
		try {
			wireMockServer.stubFor(
					delete(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/repository/branches/" + UrlEncoder.urlEncode(branchName)))
							.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
							.willReturn(aResponse()
									.withStatus(204)));
		} catch (GitLabApiException e) {
			e.printStackTrace();
		}
	}

	private void setupCreateMergeRequestStub(Long projectId, Map<String, Object> customProperties) {
		wireMockServer.stubFor(
				post(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/merge_requests"))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(GitlabMockUtil.get(GitlabAction.CREATE_MR, customProperties))));
	}

	private void setupAcceptMergeRequestStub(Long projectId, Long mrId, Map<String, Object> customProperties, boolean hasPipelines, boolean hasAllApprovals) {
		wireMockServer.stubFor(
				put(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/merge_requests/" + mrId + "/merge"))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.withRequestBody(containing("merge_when_pipeline_succeeds=" + hasPipelines))
						.willReturn(aResponse()
								.withStatus(hasAllApprovals ? 200 : 405)
								.withHeader("Content-Type", "application/json")
								.withBody(hasAllApprovals ? GitlabMockUtil.get(GitlabAction.ACCEPT_MR, customProperties) : "{\"message\":\"405 Method Not Allowed\"}")));
	}

	private void setupGetMergeRequestStub(Long projectId, Long mrId, Map<String, Object> customProperties) {
		wireMockServer.stubFor(
				get(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/merge_requests/" + mrId))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(GitlabMockUtil.get(GitlabAction.GET_MR, customProperties))));
	}

	private void setupUpdateMergeRequestStub(Long projectId, Long mrId, Map<String, Object> customProperties) {
		wireMockServer.stubFor(
				put(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/merge_requests/" + mrId))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(GitlabMockUtil.get(GitlabAction.UPDATE_MR, customProperties))));
	}

	private void setupGetEmptyOpenMergeRequestStub(Long projectId) {
		wireMockServer.stubFor(
				get(urlMatching(API_PREFIX + "projects/" + projectId + "/merge_requests\\?state=opened.*"))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody("[]")));
	}

	private void setupGetOpenMergeRequestStub(Long projectId) {
		setupGetOpenMergeRequestStub(projectId, GitlabMockUtil.get(GitlabAction.GET_OPEN_MRS, null));
	}

	private void setupGetOpenMergeRequestStub(Long projectId, String body) {
		wireMockServer.stubFor(
				get(urlMatching(API_PREFIX + "projects/" + projectId + "/merge_requests\\?state=opened.*"))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(body)));
	}

	private void setupGetFileFromRepositoryRequestStub(Long projectId, String filePath, String ref) {
		try {
			wireMockServer.stubFor(
					get(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/repository/files/" + UrlEncoder.urlEncode(filePath)))
							.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
							.withQueryParam("ref", WireMock.equalTo(ref))
							.willReturn(aResponse()
									.withHeader("Content-Type", "application/json")
									.withBody(GitlabMockUtil.get(GitlabAction.GET_FILE, null))));
		} catch (GitLabApiException e) {
			e.printStackTrace();
		}
	}

	private void setupGetMRPipelinesRequestStub(Long projectId, Long mrId, boolean hasPipelines) {
		wireMockServer.stubFor(
				get(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/merge_requests/" + mrId + "/pipelines"))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiToken))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(hasPipelines ? GitlabMockUtil.get(GitlabAction.LIST_MR_PIPELINES, null) : "[]")));
	}

	private void setupApproveMergeRequestStub(Long projectId, Long mrId, Map<String, Object> customProperties) {
		wireMockServer.stubFor(
				post(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/merge_requests/" + mrId + "/approve"))
						.withHeader(API_AUTH_KEY_NAME, WireMock.equalTo(apiTokenApprover.orElseThrow(() -> new IllegalStateException("When no approver api token is set, an ApproveMergeRequest call is not expected"))))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(GitlabMockUtil.get(GitlabAction.APPROVE_MR, customProperties))));
	}

	private void setupCompareBranchesRequestStub(Long projectId, String from, String to, boolean hasDiff) {
		wireMockServer.stubFor(
				get(urlPathEqualTo(API_PREFIX + "projects/" + projectId + "/repository/compare"))
						.withQueryParam("from", WireMock.equalTo(from))
						.withQueryParam("to", WireMock.equalTo(to))
						.withQueryParam("straight", WireMock.equalTo("true"))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(hasDiff ? GitlabMockUtil.get(GitlabAction.COMPARE_DIFF_BRANCHES, null) : GitlabMockUtil.get(GitlabAction.COMPARE_NO_DIFF_BRANCHES, null))));
	}

	private void setupGetUserStub() {
		wireMockServer.stubFor(
				get(urlPathEqualTo(API_PREFIX + "user"))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody(GitlabMockUtil.get(GitlabAction.GET_USER, null))));
	}

	private MergeRequest createMr(Long iid, String sourceBranch, String targetBranch, Boolean mergeWhenPipelineSucceeds) {
		MergeRequest mr = new MergeRequest();
		mr.setProjectId(GitlabMockUtil.PROJECT_ID);
		mr.setIid(iid);
		mr.setSourceBranch(sourceBranch);
		mr.setTargetBranch(targetBranch);
		mr.setMergeWhenPipelineSucceeds(mergeWhenPipelineSucceeds);
		mr.setMergeStatus("can_be_merged");
		return mr;
	}

	private String toJson(Object value) throws JsonProcessingException {
		ObjectMapper mapper = new JacksonJson().getObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		return mapper.writeValueAsString(value);
	}
}
