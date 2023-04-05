package com.unblu.ucascade.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gitlab4j.api.Constants.MergeRequestState;
import org.gitlab4j.api.utils.JacksonJson;
import org.gitlab4j.api.webhook.MergeRequestEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.unblu.ucascade.util.GitlabMockUtil.GitlabAction;

import controller.model.CascadeResult;
import controller.model.Config;
import controller.model.ConfigBranches;
import controller.model.DeleteBranchResult;
import controller.model.MergeRequestResult;
import controller.model.MergeRequestSimple;
import controller.model.MergeRequestUcascadeState;
import util.JsonUtils;

class JsonUtilsTest {

	@Test
	void testParseConfig() throws Exception {
		Config expected = new Config()
				.addBranch(new ConfigBranches()
						.withSourceBranchPattern("main/1\\.2\\.x")
						.withTargetBranch("main/1.3.x"))
				.addBranch(new ConfigBranches()
						.withSourceBranchPattern("main/1\\.3\\.x/[0-9]{4}\\.[0-9]{2}")
						.withTargetBranch("main/1.3.x"))
				.addBranch(new ConfigBranches()
						.withSourceBranchPattern("main/1\\.3\\.x")
						.withTargetBranch("main/2.0.x"))
				.addBranch(new ConfigBranches()
						.withSourceBranchPattern("main/2\\.0\\.x/[0-9]{4}\\.[0-9]{2}")
						.withTargetBranch("main/2.0.x"));

		// Due to https://github.com/docToolchain/docToolchain/issues/898 we need a copy inside the _documentation project because it can't access the java project
		Path file = Path.of("_documentation/src/docs/tech-docs/ucascade.json");

		// Read the current content to see if the parser works:
		String content = Files.readString(file);
		Config config = JsonUtils.getConfigFromString(content);

		// Update the file derived from the Java model, so that we are sure it stays up-to-date in the docs:
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		String expectedContent = mapper.writeValueAsString(expected);
		Files.writeString(file, expectedContent);

		Assertions.assertEquals(expected, config);
		Assertions.assertEquals(expectedContent, content);
	}

	@Test
	void testParseMergeRequest() throws Exception {
		Long projectId = 23L;
		Long mrNumber = 122L;
		Long userId = 9382L;
		String sourceBranch = "feature/1.3.x/some-change";
		String targetBranch = "main/1.3.x";
		String mrState = MergeRequestState.MERGED.toValue();
		String mergeCommitSha = "0c6f9d312b924bff313f60db2f269b5f4901cd95";
		String mrAction = "merge";

		MergeRequestSimple expected = new MergeRequestSimple(projectId, mrNumber, userId, sourceBranch, targetBranch, mrState, mergeCommitSha, mrAction, "replay-394720");

		// Due to https://github.com/docToolchain/docToolchain/issues/898 we need a copy inside the _documentation project because it can't access the java project
		Path file = Path.of("_documentation/src/docs/tech-docs/replay.json");

		// Read the current content to see if the parser works:
		String content = Files.readString(file);
		MergeRequestSimple mr = JsonUtils.getMergeRequestFromString(content);

		// Update the file derived from the Java model, so that we are sure it stays up-to-date in the docs:
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		String expectedContent = mapper.writeValueAsString(expected);
		Files.writeString(file, expectedContent);

		Assertions.assertEquals(expected, mr);
		Assertions.assertEquals(expectedContent, content);
	}

	@Test
	void testUcascadeNonBlockingResponseFile() throws Exception {
		CascadeResult expected = createCascadeResult();
		checkMergeRequestResultFile(expected, CascadeResult.class, "_documentation/src/docs/tech-docs/ucascade-non-blocking-response.json");
	}

	@Test
	void testUcascadeBlockingResponseFile() throws Exception {
		// corresponds to the merge event of MR !25 targeting branch main/1.3.x
		// that was an auto-merge request for MR !21 between main/1.2.x and main/1.3.x
		// branch mr21_main/1.2.x was not deleted by GitLab
		// a new cascade mr to main is created -> MR !38
		// other auto-MR open (!34) between main/1.2.x and main/1.3.x was still open (and waiting for !25 to be merged first)

		MergeRequestResult previousAutoMrMerged = new MergeRequestResult();
		previousAutoMrMerged.setId(34L);
		previousAutoMrMerged.setIid(34L);
		previousAutoMrMerged.setProjectId(1L);
		previousAutoMrMerged.setTitle("[ucascade] Auto MR: 'main/1.2.x' -> 'main/1.3.x' (!29)");
		previousAutoMrMerged.setDescription("Automatic cascade merge request: `test` !29 --> `main/1.2.x` --> `main/1.3.x`");
		previousAutoMrMerged.setState("opened");
		previousAutoMrMerged.setDetailedMergeStatus("mergeable");
		previousAutoMrMerged.setHasConflicts(false);
		previousAutoMrMerged.setSourceBranch("mr29_main/1.2.x");
		previousAutoMrMerged.setTargetBranch("main/1.3.x");
		previousAutoMrMerged.setWebUrl("https://gitlab.example.com/root/some-project/-/merge_requests/34");
		previousAutoMrMerged.setUcascadeState(MergeRequestUcascadeState.MERGED);

		MergeRequestResult createdAutoMr = new MergeRequestResult();
		createdAutoMr.setAssigneeId(40L);
		createdAutoMr.setId(38L);
		createdAutoMr.setIid(38L);
		createdAutoMr.setProjectId(1L);
		createdAutoMr.setTitle("[ucascade] Auto MR: 'main/1.3.x' -> 'develop' (!25)");
		createdAutoMr.setDescription("Automatic cascade merge request: `test` !24 --> `main/1.2.x` !25 --> `main/1.3.x` --> develop");
		createdAutoMr.setState("opened");
		createdAutoMr.setDetailedMergeStatus("broken_status");
		createdAutoMr.setHasConflicts(true);
		createdAutoMr.setSourceBranch("mr25_main/1.3.x");
		createdAutoMr.setTargetBranch("main");
		createdAutoMr.setWebUrl("https://gitlab.example.com/root/some-project/-/merge_requests/38");
		createdAutoMr.setUcascadeState(MergeRequestUcascadeState.NOT_MERGED_CONFLICTS);

		CascadeResult expected = new CascadeResult();
		expected.setBuildCommit("6af21ad");
		expected.setBuildTimestamp("2022-01-01T07:21:58.378413Z");
		expected.setGitlabEventUUID("62940263-b495-4f7e-b0e8-578c7307f13d");
		expected.setPreviousAutoMrMerged(previousAutoMrMerged);
		expected.setCreatedAutoMr(createdAutoMr);
		expected.setExistingBranchDelete(new DeleteBranchResult("mr21_main/1.2.x"));

		checkMergeRequestResultFile(expected, CascadeResult.class, "_documentation/src/docs/tech-docs/ucascade-blocking-response.json");
	}

	private CascadeResult createCascadeResult() {
		CascadeResult result = new CascadeResult();
		result.setBuildCommit("6af21ad");
		result.setBuildTimestamp("2022-01-01T07:21:58.378413Z");
		result.setGitlabEventUUID("62940263-b495-4f7e-b0e8-578c7307f13d");
		return result;
	}

	private <T> void checkMergeRequestResultFile(T expected, Class<T> cls, String filePath) throws IOException, JsonProcessingException, JsonMappingException {
		// Due to https://github.com/docToolchain/docToolchain/issues/898 we need a copy inside the _documentation project because it can't access the java project
		Path file = Path.of(filePath);

		// Read the current content to see if the parser works:
		String content = Files.readString(file);

		// Update the file derived from the Java model, so that we are sure it stays up-to-date in the docs:
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.setSerializationInclusion(Include.NON_NULL);
		T actual;
		try {
			actual = mapper.readValue(content, cls);
		} catch (Exception e) {
			actual = null;
		}
		String expectedContent = mapper.writeValueAsString(expected);
		Files.writeString(file, expectedContent);

		Assertions.assertEquals(expected, actual);
		Assertions.assertEquals(expectedContent, content);
	}

	@Test
	void testToSimpleEvent() throws Exception {
		String content = GitlabMockUtil.get(GitlabAction.EVENT_MR_MERGED, null);
		MergeRequestEvent mrEvent = new JacksonJson().getObjectMapper().readValue(content, MergeRequestEvent.class);
		MergeRequestSimple result = JsonUtils.toSimpleEvent(mrEvent, GitlabMockUtil.GITLAB_EVENT_UUID);

		MergeRequestSimple expected = GitlabMockUtil.createDefaultMREvent();
		Assertions.assertEquals(expected, result);
	}

}
