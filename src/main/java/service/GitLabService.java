package service;

import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.AcceptMergeRequestParams;
import org.gitlab4j.api.models.Assignee;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestParams;
import org.gitlab4j.models.Constants.MergeRequestState;

import controller.model.CascadeResult;
import controller.model.DeleteBranchResult;
import controller.model.MergeRequestResult;
import controller.model.MergeRequestSimple;
import controller.model.MergeRequestUcascadeState;
import io.quarkus.info.BuildInfo;
import io.quarkus.info.GitInfo;
import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.mutiny.core.eventbus.EventBus;
import util.ConfigurationUtils;

@ApplicationScoped
public class GitLabService {

	public static final String MERGE_REQUEST_EVENT = "merge-request-event";

	@Inject
	EventBus resultsBus;

	@ConfigProperty(name = "gitlab.host", defaultValue = "https://gitlab.com")
	String gitLabHost;

	@ConfigProperty(name = "gitlab.api.token")
	String apiToken;

	@ConfigProperty(name = "gitlab.api.token.approver")
	Optional<String> apiTokenApprover;

	@Inject
	GitInfo gitInfo;

	@Inject
	BuildInfo buildInfo;

	private static final String UCASCADE_CONFIGURATION_FILE = "ucascade.json";
	private static final String UCASCADE_TAG = "[ucascade]";
	private static final String UCASCADE_BRANCH_PATTERN_PREFIX = "^mr(\\d+)_";
	private static final String UCASCADE_BRANCH_PATTERN = UCASCADE_BRANCH_PATTERN_PREFIX + ".+";
	private static final int MAX_RETRY_ATTEMPTS = 60;

	private GitLabApi gitlab;
	private GitLabApi gitlabApprover;
	private Long ucascadeUser;

	@PostConstruct
	void init() throws GitLabApiException {
		gitlab = new GitLabApi(gitLabHost, apiToken);
		apiTokenApprover.ifPresent(
				value -> gitlabApprover = apiToken.equals(value) ? gitlab : new GitLabApi(gitLabHost, value));
		ucascadeUser = gitlab.getUserApi().getCurrentUser().getId();
	}

	@Blocking // Will be called on a worker thread
	@ConsumeEvent(MERGE_REQUEST_EVENT)
	public CascadeResult mergeRequest(MergeRequestSimple mrEvent) {
		String gitlabEventUUID = mrEvent.getGitlabEventUUID();

		CascadeResult result = createResult(gitlabEventUUID);

		String mrState = mrEvent.getMrState();
		String mrAction = mrEvent.getMrAction();

		if (mrState.equals("merged") && mrAction.equals("merge")) {
			handleMrMergeEvent(mrEvent, gitlabEventUUID, result);
		} else if (mrState.equals("closed") && mrAction.equals("close")) {
			handleMrCloseEvent(mrEvent, gitlabEventUUID, result);
		} else {
			handleSkipEvent(mrEvent, gitlabEventUUID, mrState, mrAction);
		}

		Log.infof("GitlabEvent: '%s' | Finished handling event with result %s",
				gitlabEventUUID, result);
		return result;
	}

	public CascadeResult createResult(String gitlabEventUUID) {
		CascadeResult result = new CascadeResult();
		result.setGitlabEventUUID(gitlabEventUUID);
		result.setBuildCommit(gitInfo.latestCommitId().substring(7));
		result.setBuildTimestamp(buildInfo.time().toString());
		return result;
	}

	private void handleMrMergeEvent(MergeRequestSimple mrEvent, String gitlabEventUUID, CascadeResult result) {
		Long userId = mrEvent.getUserId();
		Long projectId = mrEvent.getProjectId();
		String sourceBranch = mrEvent.getSourceBranch();
		String targetBranch = mrEvent.getTargetBranch();
		Long mrNumber = mrEvent.getMrNumber();
		String mergeSha = mrEvent.getStartCommitSha();
		String prevSourceBranch = removeMrPrefixPattern(sourceBranch);

		Log.infof("GitlabEvent: '%s' | Merge MR Event. Project: '%d', User: '%d', Target: '%s', MergeRequestNumber: '%d'",
				gitlabEventUUID, projectId, userId, targetBranch, mrNumber);

		try {
			mergePreviousAutoMr(result, gitlabEventUUID, projectId, targetBranch, prevSourceBranch);
		} catch (Exception e) {
			Log.warnf(e, "GitlabEvent: '%s' | Exception while merging previous auto merge request. Project: '%d', Target: '%s', PrevSourceBranch: '%s'",
					gitlabEventUUID, projectId, targetBranch, prevSourceBranch);
			result.setPreviousAutoMrMergedError(e.getMessage());
		}

		try {
			createAutoMr(result, gitlabEventUUID, projectId, sourceBranch, targetBranch, mrNumber, mergeSha);
		} catch (Exception e) {
			Log.warnf(e, "GitlabEvent: '%s' | Exception while creating auto merge request. Project: '%d', User: '%d', SourceBranch: '%s', TargetBranch: '%s', MergeRequestNumber: '%d', MergeSha: '%s'",
					gitlabEventUUID, projectId, userId, sourceBranch, targetBranch, mrNumber, mergeSha);
			result.setCreatedAutoMrError(e.getMessage());
		}

		try {
			deleteExistingBranch(result, gitlabEventUUID, projectId, sourceBranch);
		} catch (Exception e) {
			Log.warnf(e, "GitlabEvent: '%s' | Exception while deleting existing branch. Project: '%d', SourceBranch: '%s'",
					gitlabEventUUID, projectId, sourceBranch);
			result.setExistingBranchDeleteError(e.getMessage());
		}
	}

	private void handleMrCloseEvent(MergeRequestSimple mrEvent, String gitlabEventUUID, CascadeResult result) {
		Long userId = mrEvent.getUserId();
		Long projectId = mrEvent.getProjectId();
		String sourceBranch = mrEvent.getSourceBranch();
		String targetBranch = mrEvent.getTargetBranch();
		Long mrNumber = mrEvent.getMrNumber();
		String prevSourceBranch = removeMrPrefixPattern(sourceBranch);

		Log.infof("GitlabEvent: '%s' | Close MR Event. Project: '%d', User: '%d', Target: '%s', MergeRequestNumber: '%d'",
				gitlabEventUUID, projectId, userId, targetBranch, mrNumber);

		try {
			deleteExistingBranch(result, gitlabEventUUID, projectId, sourceBranch);
		} catch (Exception e) {
			Log.warnf(e, "GitlabEvent: '%s' | Exception while deleting existing branch. Project: '%d', SourceBranch: '%s'",
					gitlabEventUUID, projectId, sourceBranch);
			result.setExistingBranchDeleteError(e.getMessage());
		}

		try {
			mergePreviousAutoMr(result, gitlabEventUUID, projectId, targetBranch, prevSourceBranch);
		} catch (Exception e) {
			Log.warnf(e, "GitlabEvent: '%s' | Exception while merging previous auto merge request. Project: '%d', Target: '%s', PrevSourceBranch: '%s'",
					gitlabEventUUID, projectId, targetBranch, prevSourceBranch);
			result.setPreviousAutoMrMergedError(e.getMessage());
		}
	}

	private void handleSkipEvent(MergeRequestSimple mrEvent, String gitlabEventUUID, String mrState, String mrAction) {
		Log.infof("GitlabEvent: '%s' | Skip event for Project: '%d', User: '%d', Target: '%s', MergeRequestNumber: '%d', State: '%s', Action: '%s'",
				gitlabEventUUID, mrEvent.getProjectId(), mrEvent.getUserId(), mrEvent.getTargetBranch(), mrEvent.getMrNumber(), mrState, mrAction);
	}

	private void mergePreviousAutoMr(CascadeResult result, String gitlabEventUUID, Long projectId, String sourceBranch, String prevSourceBranch) {
		String branchPattern = addMrPrefixPattern(prevSourceBranch);

		getOpenMergeRequests(gitlabEventUUID, projectId)
				.filter(openMr -> openMr.getTargetBranch().equals(sourceBranch) &&
						openMr.getSourceBranch().matches(branchPattern))
				.min(Comparator.comparingLong(MergeRequest::getIid))
				.ifPresent(mr -> {
					Log.infof("GitlabEvent: '%s' | Found auto merge request between '%s' and '%s' - iid: '%s', mergeWhenPipelineSucceeds: '%s'", gitlabEventUUID, prevSourceBranch, sourceBranch, mr.getIid(), mr.getMergeWhenPipelineSucceeds());
					if (mr.getMergeWhenPipelineSucceeds() == Boolean.FALSE) {
						if (mr.getHasConflicts() != null && mr.getHasConflicts()) {
							// wait some seconds in order for GitLab to have time to update the merge request status
							sleepSeconds(30);
							mr = getExistingMr(gitlabEventUUID, mr);
						}
						MergeRequestResult mrResult = acceptAutoMergeRequest(gitlabEventUUID, mr);
						result.setPreviousAutoMrMerged(mrResult);
					}
				});
	}

	private void createAutoMr(CascadeResult result, String gitlabEventUUID, Long projectId, String prevMrSourceBranch, String sourceBranch, Long mrNumber, String mergeSha) {
		String branchModel = getBranchModelConfigurationFile(gitlabEventUUID, projectId, mergeSha);
		String nextMainBranch = ConfigurationUtils.getNextTargetBranch(branchModel, sourceBranch);

		if (nextMainBranch != null) {
			Branch branch = getBranch(gitlabEventUUID, projectId, nextMainBranch);
			if (!Branch.isValid(branch)) {
				throw new IllegalStateException(String.format("GitlabEvent: '%s' | Branch named '%s' does not exist in project '%d'. Please check the ucascade configuration file.", gitlabEventUUID, nextMainBranch, projectId));
			}
			if (haveDiff(gitlabEventUUID, projectId, mergeSha, nextMainBranch)) {
				String tmpBranchName = "mr" + mrNumber + "_" + sourceBranch;
				createBranch(gitlabEventUUID, projectId, tmpBranchName, mergeSha);
				sleepSeconds(5); // give Gitlab time to recognize newly created branch
				MergeRequestResult mrResult = merge(gitlabEventUUID, projectId, sourceBranch, mrNumber, tmpBranchName, nextMainBranch);
				result.setCreatedAutoMr(mrResult);
			} else {
				Log.infof("GitlabEvent: '%s' | No diff between commit '%s' and branch '%s' - nothing to do", gitlabEventUUID, mergeSha, nextMainBranch);
			}
		} else {
			Log.infof("GitlabEvent: '%s' | No target branch for '%s' found - nothing to do", gitlabEventUUID, sourceBranch);
		}
	}

	private void deleteExistingBranch(CascadeResult result, String gitlabEventUUID, Long projectId, String prevMrSourceBranch) {
		if (prevMrSourceBranch.matches(UCASCADE_BRANCH_PATTERN)) {
			boolean deleted = deleteBranch(projectId, prevMrSourceBranch);
			if (deleted) {
				result.setExistingBranchDelete(new DeleteBranchResult(prevMrSourceBranch));
				Log.infof("GitlabEvent: '%s' | Deleted branch '%s'", gitlabEventUUID, prevMrSourceBranch);
			}
		}
	}

	private Branch createBranch(String gitlabEventUUID, Long project, String branchName, String ref) {
		try {
			Log.infof("GitlabEvent: '%s' | Creating branch: '%s' from '%s'", gitlabEventUUID, branchName, ref);
			return gitlab.getRepositoryApi().createBranch(project, branchName, ref);
		} catch (GitLabApiException e) {
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot create branch named '%s' from '%s' in project '%d'", gitlabEventUUID, branchName, ref, project), e);
		}
	}

	private MergeRequestResult merge(String gitlabEventUUID, Long project, String sourceBranchOfSourceBranch, Long prevMergedMRNumber, String sourceBranch, String targetBranch) {
		MergeRequest mr = createAutoMergeRequest(gitlabEventUUID, project, prevMergedMRNumber, sourceBranch, targetBranch);
		Long iid = mr.getIid();

		// approve auto merge-request
		if (gitlabApprover != null) {
			approveMergeRequest(gitlabEventUUID, mr);
		}

		String branchPattern = addMrPrefixPattern(sourceBranchOfSourceBranch);

		// do not merge if other merge requests exist between the same source and target branches
		Optional<MergeRequest> findConcurrentMr = getOpenMergeRequests(gitlabEventUUID, project)
				.filter(openMr -> openMr.getIid() < iid &&
						openMr.getSourceBranch().matches(branchPattern) &&
						openMr.getTargetBranch().equals(targetBranch))
				.sorted(Comparator.comparingLong(MergeRequest::getIid))
				.findFirst();
		if (findConcurrentMr.isEmpty()) {
			return acceptAutoMergeRequest(gitlabEventUUID, mr);
		}
		MergeRequestResult result = new MergeRequestResult(mr);
		result.setUcascadeState(MergeRequestUcascadeState.NOT_MERGED_CONCURRENT_MRS);
		Log.infof("GitlabEvent: '%s' | MR '!%d' in project '%d' was not merged, because of concurrent MR already open '%d'", gitlabEventUUID, mr.getIid(), project, findConcurrentMr.get().getIid());
		return result;
	}

	private MergeRequest createAutoMergeRequest(String gitlabEventUUID, Long project, Long prevMergedMRNumber, String sourceBranch, String targetBranch) {
		String description = buildDescription(gitlabEventUUID, project, sourceBranch, targetBranch);
		String sourceBranchPretty = removeMrPrefixPattern(sourceBranch);
		MergeRequestParams mrParams = new MergeRequestParams();
		mrParams.withSourceBranch(sourceBranch);
		mrParams.withTargetBranch(targetBranch);
		mrParams.withTitle(String.format("%s Auto MR: '%s' -> '%s' (!%d)", UCASCADE_TAG, sourceBranchPretty, targetBranch, prevMergedMRNumber));
		mrParams.withDescription(description);
		mrParams.withAssigneeId(ucascadeUser);
		mrParams.withRemoveSourceBranch(true);

		MergeRequest mr;
		try {
			Log.infof("GitlabEvent: '%s' | Creating MR: '%s' -> '%s'", gitlabEventUUID, sourceBranch, targetBranch);
			MergeRequestApi mrApi = gitlab.getMergeRequestApi();
			mr = mrApi.createMergeRequest(project, mrParams);
		} catch (GitLabApiException e) {
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot create merge request from '%s' into '%s' in project '%d'", gitlabEventUUID, sourceBranch, targetBranch, project), e);
		}

		return mr;
	}

	private String buildDescription(String gitlabEventUUID, Long project, String sourceBranch, String targetBranch) {
		StringBuilder descriptionBuilder = new StringBuilder(":twisted_rightwards_arrows: _Automatic cascade merge request_: ");
		Long prevMergeRequestNumber = getPrevMergeRequestNumber(sourceBranch);
		TreeMap<Long, MergeRequest> cascadedMrs = getCascadedMrs(gitlabEventUUID, project, prevMergeRequestNumber);
		Deque<String> cascadedBranches = getCascadedBranches(gitlabEventUUID, project, sourceBranch, targetBranch, prevMergeRequestNumber, cascadedMrs);
		for (String branch : cascadedBranches) {
			descriptionBuilder.append(String.format("%s", branch));
		}
		if (!cascadedMrs.isEmpty()) {
			descriptionBuilder.append("\n");
			descriptionBuilder.append("\n");
			descriptionBuilder.append("---");
			descriptionBuilder.append("\n");
			descriptionBuilder.append("\n");
			descriptionBuilder.append("<details>");
			descriptionBuilder.append("<summary>");
			descriptionBuilder.append("<strong>");
			descriptionBuilder.append(":notepad_spiral: Original description");
			descriptionBuilder.append("</strong>");
			descriptionBuilder.append("</summary>");
			descriptionBuilder.append("\n");
			descriptionBuilder.append("\n");
			String prevDescription = cascadedMrs.firstEntry().getValue().getDescription();
			if (prevDescription == null || prevDescription.isBlank()) {
				descriptionBuilder.append("_No description_");
			} else {
				descriptionBuilder.append(prevDescription);
			}
			descriptionBuilder.append("\n");
			descriptionBuilder.append("</details>");
		}
		return descriptionBuilder.toString();
	}

	private TreeMap<Long, MergeRequest> getCascadedMrs(String gitlabEventUUID, Long project, Long prevMergeRequestNumber) {
		TreeMap<Long, MergeRequest> cascadedMrs = new TreeMap<>();
		Long pastMrNumber = null;
		Long currMrNumber = prevMergeRequestNumber;
		while (currMrNumber != null && !currMrNumber.equals(pastMrNumber)) {
			MergeRequest currMr = getMr(gitlabEventUUID, project, currMrNumber);
			if (currMr != null) {
				pastMrNumber = currMrNumber;
				cascadedMrs.put(currMrNumber, currMr);
				currMrNumber = getPrevMergeRequestNumber(currMr.getSourceBranch());
			}
		}

		return cascadedMrs;
	}

	private Deque<String> getCascadedBranches(String gitlabEventUUID, Long project, String sourceBranch, String targetBranch, Long prevMergeRequestNumber, Map<Long, MergeRequest> cascadedMrs) {
		Deque<String> cascadedBranches = new ArrayDeque<>();
		final String separator = "→";
		cascadedBranches.push(formatCascadeElement(null, null, targetBranch));
		Long pastMrNumber = null;
		Long currMrNumber = prevMergeRequestNumber;
		cascadedBranches.push(formatCascadeElement(separator, null, sourceBranch));
		while (currMrNumber != null && !currMrNumber.equals(pastMrNumber)) {
			MergeRequest currMr = cascadedMrs.get(currMrNumber);
			if (currMr == null) {
				currMr = getMr(gitlabEventUUID, project, currMrNumber);
			}
			if (currMr != null) {
				pastMrNumber = currMrNumber;
				String itBranch = currMr.getSourceBranch();
				cascadedBranches.push(formatCascadeElement(separator, currMrNumber, itBranch));
				currMrNumber = getPrevMergeRequestNumber(itBranch);
			}
		}

		return cascadedBranches;
	}

	private MergeRequest approveMergeRequest(String gitlabEventUUID, MergeRequest mr) {
		final Long project = mr.getProjectId();
		final Long mrNumber = mr.getIid();
		MergeRequestApi mrApi = gitlabApprover.getMergeRequestApi();
		try {
			Log.infof("GitlabEvent: '%s' | Approving MR '!%d' in project '%d'", gitlabEventUUID, mrNumber, project);
			mr = mrApi.approveMergeRequest(project, mrNumber, null);
		} catch (GitLabApiException e) {
			assignMrToCascadeResponsible(gitlabEventUUID, mr);
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot approve merge request '!%d' in project '%d'", gitlabEventUUID, mrNumber, project), e);
		}

		return mr;
	}

	private boolean isMergeRequestApproved(String gitlabEventUUID, MergeRequest mr) {
		final Long project = mr.getProjectId();
		final Long mrNumber = mr.getIid();
		try {
			Integer approvalsLeft = gitlab.getMergeRequestApi()
					.getApprovals(project, mrNumber)
					.getApprovalsLeft();
			return approvalsLeft == null || approvalsLeft <= 0;
		} catch (GitLabApiException e) {
			assignMrToCascadeResponsible(gitlabEventUUID, mr);
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot retrieve approvals for merge request '!%d' in project '%d'", gitlabEventUUID, mrNumber, project), e);
		}
	}

	private MergeRequestResult acceptAutoMergeRequest(String gitlabEventUUID, MergeRequest mr) {
		final Long mrNumber = mr.getIid();
		final Long project = mr.getProjectId();

		if (!isMergeRequestApproved(gitlabEventUUID, mr)) {
			mr = assignMrToCascadeResponsible(gitlabEventUUID, mr);
			Log.warnf("GitlabEvent: '%s' | Merge request '!%d' in project '%d' cannot be accepted because it does not have the required amount of approvals", gitlabEventUUID, mrNumber, project);
			MergeRequestResult result = new MergeRequestResult(mr);
			result.setUcascadeState(MergeRequestUcascadeState.NOT_MERGED_MISSING_APPROVALS);
			return result;
		}

		// wait for MR to become ready
		mr = waitForMrReady(gitlabEventUUID, mr);
		String mergeStatus = mr.getDetailedMergeStatus();

		MergeRequestApi mrApi = gitlab.getMergeRequestApi();
		MergeRequestUcascadeState state = MergeRequestUcascadeState.NOT_MERGED_UNKNOWN_REASON;
		if (mergeStatus.matches("mergeable|ci_still_running|ci_must_pass|status_checks_must_pass")) {
			// Prepare merge:
			String sourceBranch = mr.getSourceBranch();
			String sourceBranchPretty = removeMrPrefixPattern(sourceBranch);
			String targetBranch = mr.getTargetBranch();
			AcceptMergeRequestParams acceptMrParams = new AcceptMergeRequestParams()
					.withMergeCommitMessage(String.format("%s Automatic merge: '%s' -> '%s'", UCASCADE_TAG, sourceBranchPretty, targetBranch))
					.withShouldRemoveSourceBranch(mr.getForceRemoveSourceBranch());

			int countDown = MAX_RETRY_ATTEMPTS;
			while (state != MergeRequestUcascadeState.MERGED && countDown-- > 0) {
				boolean hasPipeline = hasPipeline(gitlabEventUUID, mr);
				acceptMrParams.withMergeWhenPipelineSucceeds(hasPipeline);
				Log.infof("GitlabEvent: '%s' | Merging MR '!%d': '%s' -> '%s' when pipeline succeeds: '%b'", gitlabEventUUID, mrNumber, sourceBranch, targetBranch, hasPipeline);
				try {
					mr = mrApi.acceptMergeRequest(project, mrNumber, acceptMrParams);
					state = MergeRequestUcascadeState.MERGED;
				} catch (GitLabApiException e) {
					if (countDown == 0) {
						Log.warnf(e, "GitlabEvent: '%s' | Cannot accept merge request '!%d' from '%s' into '%s' in project '%d'", gitlabEventUUID, mrNumber, sourceBranch, targetBranch, project);
						state = MergeRequestUcascadeState.NOT_MERGED_UNKNOWN_REASON;
						mr = assignMrToCascadeResponsible(gitlabEventUUID, mr);
					} else {
						Log.infof("GitlabEvent: '%s' | Merge failed for MR '!%d', status '%s'. Retrying... %d", gitlabEventUUID, mrNumber, mergeStatus, Math.abs(countDown - MAX_RETRY_ATTEMPTS));
						sleepSeconds(1);
					}
				}
			}
		} else {
			// if possible, assign merge responsibility to a real user
			Log.infof("GitlabEvent: '%s' | Automatic merge failed - status: '%s'. Finding cascade responsible for MR '!%d'", gitlabEventUUID, mergeStatus, mrNumber);
			mr = assignMrToCascadeResponsible(gitlabEventUUID, mr);
			boolean hasConflicts = mr.getHasConflicts() != null && mr.getHasConflicts();
			if (hasConflicts) {
				Log.infof("GitlabEvent: '%s' | Abort: conflict detected in MR '!%d'", gitlabEventUUID, mr.getIid());
				state = MergeRequestUcascadeState.NOT_MERGED_CONFLICTS;
			} else {
				Log.warnf("GitlabEvent: '%s' | MR '!%d', status '%s', cannot be merged for unknown reasons", gitlabEventUUID, mrNumber, mergeStatus);
			}
		}

		MergeRequestResult result = new MergeRequestResult(mr);
		result.setUcascadeState(state);
		return result;
	}

	/**
	 * Assign a merge-request to the cascade responsible user(s). The cascade responsible users are all the assignees plus the merger of the merge-request that
	 * initiated the cascade.
	 *
	 * @param gitlabEventUUID the gitlab event UUID
	 * @param mr the merge-request that will be assigned to the cascade responsible
	 * @return the updated merge-request object, where the assignee is the cascade responsible user
	 */
	private MergeRequest assignMrToCascadeResponsible(String gitlabEventUUID, MergeRequest mr) {
		final Long mrNumber = mr.getIid();
		final Long project = mr.getProjectId();
		List<Long> cascadeResponsibleIds = getCascadeResponsibleIds(gitlabEventUUID, project, getPrevMergeRequestNumber(mr.getSourceBranch()));
		if (cascadeResponsibleIds != null) {
			Log.infof("GitlabEvent: '%s' | Assigning MR '!%d' to cascade responsible(s) with id(s): '%s'", gitlabEventUUID, mrNumber, cascadeResponsibleIds);
			try {
				MergeRequestParams mrParams = new MergeRequestParams();
				if (cascadeResponsibleIds.size() > 1) {
					mrParams.withAssigneeIds(cascadeResponsibleIds);
				} else {
					mrParams.withAssigneeId(cascadeResponsibleIds.get(0));
				}
				mr = gitlab.getMergeRequestApi().updateMergeRequest(project, mrNumber, mrParams);
			} catch (GitLabApiException e) {
				Log.warnf(e, "GitlabEvent: '%s' | MR '!%d' cannot change the assignee to user %d", gitlabEventUUID, mrNumber, cascadeResponsibleIds);
			}
		}
		return mr;
	}

	private String addMrPrefixPattern(String branchName) {
		return UCASCADE_BRANCH_PATTERN_PREFIX + Pattern.quote(branchName);
	}

	private MergeRequest waitForMrReady(String gitlabEventUUID, MergeRequest mr) {
		boolean approverExists = gitlabApprover != null;
		int countDown = MAX_RETRY_ATTEMPTS;
		String mrStatus = mr.getDetailedMergeStatus();
		while (!isMrReady(mrStatus, approverExists, mr.getHasConflicts()) && countDown-- > 0) {
			sleepSeconds(1);
			mr = getExistingMr(gitlabEventUUID, mr);
			mrStatus = mr.getDetailedMergeStatus();
		}

		if (countDown < 0) {
			mr = assignMrToCascadeResponsible(gitlabEventUUID, mr);
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Timeout: MR '!%d' is locked in the '%s' status", gitlabEventUUID, mr.getIid(), mrStatus));
		}
		return mr;
	}

	private MergeRequest getExistingMr(String gitlabEventUUID, MergeRequest mr) {
		try {
			MergeRequestApi mrApi = gitlab.getMergeRequestApi();
			return mrApi.getMergeRequest(mr.getProjectId(), mr.getIid());
		} catch (GitLabApiException e) {
			mr = assignMrToCascadeResponsible(gitlabEventUUID, mr);
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot retrieve MR '!%d'", gitlabEventUUID, mr.getIid()), e);
		}
	}

	public static boolean isMrReady(String mrStatus, boolean approverExists, Boolean hasConflicts) {
		return !(mrStatus.matches("unchecked|checking|preparing|approvals_syncing") ||
				(approverExists && mrStatus.matches("not_approved")) ||
				!(hasConflicts != null && hasConflicts) && mrStatus.matches("broken_status"));
	}

	private Branch getBranch(String gitlabEventUUID, Long project, String branchName) {
		try {
			return gitlab.getRepositoryApi().getBranch(project, branchName);
		} catch (GitLabApiException e) {
			if (e.getHttpStatus() == 404) {
				// if the branch doesn't exist, return null
				return null;
			}
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot retrieve branch '%s'", gitlabEventUUID, branchName), e);
		}
	}

	private boolean deleteBranch(Long project, String branchName) {
		try {
			gitlab.getRepositoryApi().deleteBranch(project, branchName);
			return true;
		} catch (GitLabApiException e) {
			// fail silently
			return false;
		}
	}

	private Stream<MergeRequest> getOpenMergeRequests(String gitlabEventUUID, Long project) {
		try {
			MergeRequestApi mrApi = gitlab.getMergeRequestApi();
			return mrApi.getMergeRequestsStream(project, MergeRequestState.OPENED);
		} catch (GitLabApiException e) {
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot retrieve open merge requests for project '%d' in '%s'", gitlabEventUUID, project, gitLabHost), e);
		}
	}

	private String getBranchModelConfigurationFile(String gitlabEventUUID, Long project, String ref) {
		try {
			String encodedContent = gitlab.getRepositoryFileApi().getFile(project, UCASCADE_CONFIGURATION_FILE, ref).getContent();
			return new String(Base64.getDecoder().decode(encodedContent));
		} catch (GitLabApiException e) {
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Configuration file '%s' not found in remote repository at '%s'", gitlabEventUUID, UCASCADE_CONFIGURATION_FILE, ref), e);
		}
	}

	private boolean hasPipeline(String gitlabEventUUID, MergeRequest mr) {
		try {
			MergeRequestApi mrApi = gitlab.getMergeRequestApi();
			return !mrApi.getMergeRequestPipelines(mr.getProjectId(), mr.getIid()).isEmpty();
		} catch (GitLabApiException e) {
			assignMrToCascadeResponsible(gitlabEventUUID, mr);
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot retrieve pipelines for merge request '!%d' in project '%d'", gitlabEventUUID, mr.getIid(), mr.getProjectId()), e);
		}
	}

	private boolean haveDiff(String gitlabEventUUID, Long project, String from, String to) {
		try {
			return !gitlab.getRepositoryApi().compare(project, from, to, true).getDiffs().isEmpty();
		} catch (GitLabApiException e) {
			throw new IllegalStateException(String.format("GitlabEvent: '%s' | Cannot get branch diff from '%s' to '%s' in project '%d'", gitlabEventUUID, from, to, project), e);
		}
	}

	private List<Long> getCascadeResponsibleIds(String gitlabEventUUID, Long project, Long mrNumber) {
		Log.infof("GitlabEvent: '%s' | Backtracing cascade responsible: get merge request '!%d' in project '%d'", gitlabEventUUID, mrNumber, project);
		MergeRequest mr = getMr(gitlabEventUUID, project, mrNumber);
		if (mr == null) {
			return null;
		}

		Long mrMerger = mr.getMergeUser().getId();
		Log.infof("GitlabEvent: '%s' | MR merger has id '%d', ucascade user has id '%d'", gitlabEventUUID, mrMerger, ucascadeUser);

		if (!Objects.equals(mrMerger, ucascadeUser)) {
			// success exit point, found the origin mr
			List<Long> cascadeResponsibleIds = mr.getAssignees().stream().map(Assignee::getId).collect(Collectors.toList());
			if (!cascadeResponsibleIds.contains(mrMerger)) {
				cascadeResponsibleIds.add(mrMerger);
			}
			return cascadeResponsibleIds;
		}

		Long prevMRNumber = getPrevMergeRequestNumber(mr.getSourceBranch());
		if (prevMRNumber == null) {
			// could not find the previous mr
			Log.warnf("GitlabEvent: '%s' | Cannot get cascade responsible of merge request '!%d' in project '%d' (could not compute the previous MR)", gitlabEventUUID, mrNumber, project);
			return null;
		}

		// get the cascade responsible for the previous mr
		List<Long> cascadeResponsibleIds = getCascadeResponsibleIds(gitlabEventUUID, project, prevMRNumber);
		if (cascadeResponsibleIds == null) {
			Log.warnf("GitlabEvent: '%s' | Cannot get cascade responsible of merge request '!%d' in project '%d'", gitlabEventUUID, mrNumber, project);
		}
		return cascadeResponsibleIds;
	}

	private MergeRequest getMr(String gitlabEventUUID, Long project, Long mrNumber) {
		try {
			return gitlab.getMergeRequestApi().getMergeRequest(project, mrNumber);
		} catch (GitLabApiException e) {
			Log.warnf(e, "GitlabEvent: '%s' | Cannot get merge request '!%d' in project '%d'", gitlabEventUUID, mrNumber, project);
			return null;
		}
	}

	public static Long getPrevMergeRequestNumber(String sourceBranchName) {
		Pattern prevMRNumberPattern = Pattern.compile(UCASCADE_BRANCH_PATTERN);
		Matcher matcher = prevMRNumberPattern.matcher(sourceBranchName);
		if (matcher.find()) {
			return Long.valueOf(matcher.group(1));
		}
		return null;
	}

	private static String removeMrPrefixPattern(String sourceBranch) {
		return sourceBranch.replaceFirst(UCASCADE_BRANCH_PATTERN_PREFIX, "");
	}

	public static String formatCascadeElement(String separator, Long mrNumber, String branchName) {
		if (mrNumber == null && separator == null) {
			return String.format("`%s`", removeMrPrefixPattern(branchName));
		} else if (separator == null) {
			return String.format("`%s` !%d ", removeMrPrefixPattern(branchName), mrNumber);
		} else if (mrNumber == null) {
			return String.format("`%s` %s ", removeMrPrefixPattern(branchName), separator);
		} else {
			return String.format("`%s` !%d %s ", removeMrPrefixPattern(branchName), mrNumber, separator);
		}
	}

	private static void sleepSeconds(int seconds) {
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
}
