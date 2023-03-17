package util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import controller.model.Config;
import controller.model.ConfigBranches;

public class ConfigurationUtils {

	public static String getNextTargetBranch(String branchModel, String sourceBranchName) {
		Config config = JsonUtils.getConfigFromString(branchModel);
		List<ConfigBranches> branches = config.getBranches();
		if (branches != null) {
			for (final ConfigBranches branch : branches) {
				String sourceBranchPatternString = branch.getSourceBranchPattern();
				Pattern sourceBranchPattern = Pattern.compile(sourceBranchPatternString);
				Matcher matcher = sourceBranchPattern.matcher(sourceBranchName);
				if (matcher.find()) {
					return branch.getTargetBranch();
				}
			}
		}

		return null;
	}
}
