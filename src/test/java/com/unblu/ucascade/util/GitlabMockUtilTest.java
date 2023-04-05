package com.unblu.ucascade.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.unblu.ucascade.util.GitlabMockUtil.GitlabAction;

import util.ConfigurationUtils;

class GitlabMockUtilTest {

	@Test
	void gitLabMockUtilTest() {
		Map<String, Object> customProperties = Map.of(
				"detailed_merge_status", "\"broken_status\"",
				"project_id", 1234);
		String body = GitlabMockUtil.get(GitlabAction.CREATE_MR, customProperties);

		assertTrue(body.contains("\"detailed_merge_status\" : \"broken_status\","));
		assertFalse(body.contains("\"detailed_merge_status\" : \"mergeable\","));
		assertTrue(body.contains("\"project_id\" : 1234,"));
		assertFalse(body.contains("\"project_id\" : 1,"));
	}

	@Test
	void testGetNextTargetBranch() throws Exception {
		String branchModelContent = Files.readString(Path.of("src/test/resources/ucascade.json"));
		String actual = ConfigurationUtils.getNextTargetBranch(branchModelContent, GitlabMockUtil.MR_EVENT_TARGET_BRANCH);
		assertEquals(GitlabMockUtil.DEFAULT_TARGET_BRANCH, actual);
	}

}
