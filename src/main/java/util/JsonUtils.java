package util;

import java.io.IOException;

import org.gitlab4j.api.webhook.MergeRequestEvent;
import org.gitlab4j.api.webhook.MergeRequestEvent.ObjectAttributes;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import controller.model.Config;
import controller.model.MergeRequestSimple;

public class JsonUtils {

	public static Config getConfigFromString(String content) {
		try {
			ObjectMapper mapper = JsonMapper.builder()
					.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
					.build();
			return mapper.readValue(content, Config.class);
		} catch (IOException ex) {
			throw new IllegalArgumentException(String.format("The String could not be parsed as Config: '%s'", content), ex);
		}
	}

	public static MergeRequestSimple getMergeRequestFromString(String content) {
		try {
			ObjectMapper mapper = JsonMapper.builder()
					.build();
			return mapper.readValue(content, MergeRequestSimple.class);
		} catch (IOException ex) {
			throw new IllegalArgumentException(String.format("The String could not be parsed as MergeRequestSimple: '%s'", content), ex);
		}
	}

	public static MergeRequestSimple toSimpleEvent(MergeRequestEvent mrEvent, String gitlabEventUUID) {
		MergeRequestSimple result = new MergeRequestSimple();

		ObjectAttributes objectAttributes = mrEvent.getObjectAttributes();
		if (objectAttributes == null) {
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | MergeRequestEvent.ObjectAttributes is null. Possible cause: error in the deserializing process", gitlabEventUUID));
		}

		result.setGitlabEventUUID(gitlabEventUUID);
		result.setUserId(mrEvent.getUser().getId());
		result.setProjectId(mrEvent.getProject().getId());
		result.setSourceBranch(objectAttributes.getSourceBranch());
		result.setTargetBranch(objectAttributes.getTargetBranch());
		result.setMrNumber(objectAttributes.getIid());
		result.setMergeCommitSha(objectAttributes.getMergeCommitSha());
		result.setMrState(objectAttributes.getState());
		result.setMrAction(objectAttributes.getAction());

		return result;
	}

	private JsonUtils() {
	}
}
