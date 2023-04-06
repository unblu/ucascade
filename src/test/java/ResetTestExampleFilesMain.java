import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.gitlab4j.api.Constants.StateEvent;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AcceptMergeRequestParams;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestParams;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ProjectHook;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.User;
import org.gitlab4j.api.models.Visibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

public class ResetTestExampleFilesMain {

	//	private static final String URL = "https://gitlab.com";
	private static final String URL = "http://localhost:9080";
	//	private static final String URL = "http://localhost:8888";
	private static final String TOKEN = "glpat-********************";
	//private static final String HOOK_URL = "http://localhost:9999";
	private static final String HOOK_URL = "https://****-****-***-***-****-****-****-****-****.eu.ngrok.io";
	private static final String HOOK_PATH = "/webhook";
	private static final String PROJECT_NAME = "test_project";
	private static final boolean RECREATE_PROJECT = true;

	public static void main(String[] args) throws Exception {
		System.out.println("Prepare mock server");
		WireMockServer wireMockServer = new WireMockServer(9999);
		wireMockServer.start();
		wireMockServer.stubFor(
				post(urlPathEqualTo(HOOK_PATH))
						.willReturn(aResponse()
								.withHeader("Content-Type", "application/json")
								.withBody("{ \"status\": \"ok\"}")));

		try (GitLabApi gitLabApi = new GitLabApi(URL, TOKEN)) {
			User currentUser = gitLabApi.getUserApi().getCurrentUser();

			//Prepare project:
			System.out.println("Prepare project");
			Project project;
			try {
				String projectPath = currentUser.getUsername() + "/" + PROJECT_NAME;
				project = gitLabApi.getProjectApi().getProject(projectPath);
				if (RECREATE_PROJECT && project != null) {
					gitLabApi.getProjectApi().deleteProject(project);
					waitForProjectToBeDeleted(gitLabApi, projectPath);
					project = null;
				}
			} catch (GitLabApiException e) {
				project = null;
			}
			if (project == null) {
				Project initProject = new Project()
						.withName(PROJECT_NAME)
						.withVisibility(Visibility.PUBLIC)
						.withInitializeWithReadme(true)
						.withDefaultBranch("main");
				project = gitLabApi.getProjectApi().createProject(initProject);
			}

			//Initialize the repository:
			System.out.println("Initialize the repository");
			Branch branch;
			try {
				branch = gitLabApi.getRepositoryApi().getBranch(project, "main");
			} catch (GitLabApiException e) {
				branch = null;
			}
			if (branch == null) {
				createFile(gitLabApi, project, "main", "test.txt", "This is a test", "Initial commit");

				branch = gitLabApi.getRepositoryApi().getBranch(project, "main");
			}
			String commitSha = branch.getCommit().getId();

			//Delete branches from previous run (if they exists)
			System.out.println("Delete branches from previous run (if they exists)");
			deleteBranch(gitLabApi, project, "feature_branch");
			deleteBranch(gitLabApi, project, "mr40_release/6.x.x");
			deleteBranch(gitLabApi, project, "mr100_release/6.x.x");

			//[WRITE RESPONSE FILE] get branch:
			System.out.println("Get branch -> gitlab_template_json/api/getBranchResponse.json");
			storeGetRequestResponse(Paths.get("src/test/resources/gitlab_template_json/api/getBranchResponse.json"), URL + "/api/v4/projects/" + project.getId() + "/repository/branches/main");

			//[WRITE RESPONSE FILE] create branch
			System.out.println("Create branch -> gitlab_template_json/api/createBranchResponse.json");
			storePostEmptyBodyRequestResponse(Paths.get("src/test/resources/gitlab_template_json/api/createBranchResponse.json"), URL + "/api/v4/projects/" + project.getId() + "/repository/branches?branch=mr90_release%2F6.x.x&ref=" + commitSha);
			deleteBranch(gitLabApi, project, "mr90_release/6.x.x");

			//ucascade file:
			System.out.println("Ucascade file");
			String expectedContent = Files.readString(Paths.get("src/test/resources/ucascade.json"));
			commitSha = ensureFileContent(gitLabApi, project, commitSha, expectedContent, "ucascade.json");

			//[WRITE RESPONSE FILE] get file:
			System.out.println("Get file -> gitlab_template_json/api/getFileFromRepository.json");
			storeGetRequestResponse(Paths.get("src/test/resources/gitlab_template_json/api/getFileFromRepository.json"), URL + "/api/v4/projects/" + project.getId() + "/repository/files/ucascade%2Ejson?ref=" + commitSha);

			//pipeline file:
			// XXX
			// System.out.println("Pipeline file");
			// String pipelineContent = """
			// 		stages:
			// 		  - build
			//
			// 		build-job:
			// 		  stage: build
			// 		  script:
			// 		    - echo "Compiling the code..."
			// 		    - echo "Compile complete."
			// 		""";
			// createFile(gitLabApi, project, "main", ".gitlab-ci.yml", pipelineContent, "Add pipeline file");

			//create release/6.x.x branch
			System.out.println("Create release/6.x.x branch");
			createBranch(gitLabApi, project, "release/6.x.x", commitSha);

			//delete all previous MRs if any?
			System.out.println("Delete all previous MRs if any?");
			List<MergeRequest> mergeRequests = gitLabApi.getMergeRequestApi().getMergeRequests(project);
			for (MergeRequest mr : mergeRequests) {
				gitLabApi.getMergeRequestApi().deleteMergeRequest(project, mr.getIid());
			}

			Branch featureBranch = createBranch(gitLabApi, project, "feature_branch", commitSha);
			for (int i = 1; i < 90; i++) {
				System.out.println("Create dummy MR " + i);
				createAndCloseMergeRequest(gitLabApi, project, featureBranch.getName(), "main", "MR " + i);
			}
			System.out.println("Create ucascade MR 90");
			Branch mr40Branch = createBranch(gitLabApi, project, "mr40_release/6.x.x", commitSha);
			createFile(gitLabApi, project, mr40Branch.getName(), "change" + System.currentTimeMillis() + ".txt", "This is a test", "My feature");
			createMergeRequest(gitLabApi, project, mr40Branch.getName(), "main", "[ucascade-TEST] Auto MR: release/6.x.x -> main (!40)");
			for (int i = 91; i < 100; i++) {
				System.out.println("Create dummy MR " + i);
				createAndCloseMergeRequest(gitLabApi, project, featureBranch.getName(), "main", "MR " + i);
			}
			//create a real merge request
			System.out.println("Create real MR");
			Branch someFeature = createBranch(gitLabApi, project, "some-feature", "release/6.x.x");
			createFile(gitLabApi, project, someFeature.getName(), "file" + System.currentTimeMillis() + ".txt", "This is a test", "My feature");
			MergeRequest mr100 = createMergeRequest(gitLabApi, project, someFeature.getName(), "release/6.x.x", "Some feature");

			//create a webhook receiver to catch the event coming from Gitlab:
			System.out.println("Create webhook receiver");
			ProjectHook hook = gitLabApi.getProjectApi().addHook(project, HOOK_URL + HOOK_PATH, false, false, true);
			Thread.sleep(1000L);
			waitForMr(gitLabApi, project, mr100.getIid());
			System.out.println("Accept real MR");
			AcceptMergeRequestParams acceptMergeParam = new AcceptMergeRequestParams()
					.withMergeCommitMessage("Merge commit message")
					// XXX .withMergeWhenPipelineSucceeds(true)
					.withShouldRemoveSourceBranch(true);
			gitLabApi.getMergeRequestApi().acceptMergeRequest(project, mr100.getIid(), acceptMergeParam);
			System.out.println("Store webhook event -> gitlab_template_json/webhook/mergedMREvent.json");
			LoggedRequest request = waitForRequest(wireMockServer);
			storeRequest(Paths.get("src/test/resources/gitlab_template_json/webhook/mergedMREvent.json"), request);
			gitLabApi.getProjectApi().deleteHook(hook);

			//[WRITE RESPONSE FILE] create auto merge request:
			System.out.println("Create auto merge request -> gitlab_template_json/api/createMRResponse.json");
			createBranch(gitLabApi, project, "mr100_release/6.x.x", "release/6.x.x");
			String createMr101Content = """
					{
					    "target_branch": "main",
					    "source_branch": "mr100_release/6.x.x",
					    "title": "Automatic cascade merge request: `something` !100 --> `release/6.x.x` --> `main`",
					    "description": ,
					    "remove_source_branch": true
					}
					""";
			JsonNode mr101Response = storePostRequestResponse(Paths.get("src/test/resources/gitlab_template_json/api/createMRResponse.json"), URL + "/api/v4/projects/" + project.getId() + "/merge_requests", createMr101Content);
			Long mr101Nr = mr101Response.get("iid").asLong();
			waitForMr(gitLabApi, project, mr101Nr);

			//[WRITE RESPONSE FILE] get MR
			System.out.println("Get merge request -> gitlab_template_json/api/getMRResponse.json");
			storeGetRequestResponse(Paths.get("src/test/resources/gitlab_template_json/api/getMRResponse.json"), URL + "/api/v4/projects/" + project.getId() + "/merge_requests/" + mr101Nr);

			//[WRITE RESPONSE FILE] get open MRs
			System.out.println("Get open MRs -> gitlab_template_json/api/getOpenMRsResponse.json");
			storeGetRequestResponse(Paths.get("src/test/resources/gitlab_template_json/api/getOpenMRsResponse.json"), URL + "/api/v4/projects/" + project.getId() + "/merge_requests?state=opened&per_page=96&page=1");

			//[WRITE RESPONSE FILE] accept auto merge request
			System.out.println("Accept auto merge request -> gitlab_template_json/api/acceptMRResponse.json");
			String accpetMr101Content = """
					{
					    "merge_commit_message": "[ucascade-TEST] Automatic merge: 'release/6.x.x' -> 'main'",
					    "should_remove_source_branch": true
					}
					""";
			// XXX "merge_when_pipeline_succeeds": true,
			storePutRequestResponse(Paths.get("src/test/resources/gitlab_template_json/api/acceptMRResponse.json"), URL + "/api/v4/projects/" + project.getId() + "/merge_requests/" + mr101Nr + "/merge", accpetMr101Content);

			System.out.println("DONE");
		}
	}

	private static void waitForMr(GitLabApi gitLabApi, Project project, Long mrIid) throws InterruptedException, GitLabApiException {
		int countDown = 30;
		MergeRequest mergeRequest = null;
		do {
			Thread.sleep(1000L);
			mergeRequest = gitLabApi.getMergeRequestApi().getMergeRequest(project, mrIid);
		} while (mergeRequest.getDetailedMergeStatus().equals("checking") && countDown-- > 0);
		if (!mergeRequest.getDetailedMergeStatus().equals("mergeable")) {
			throw new IllegalStateException("Wrong detailed_merge_status for MR: " + mergeRequest);
		}
	}

	private static void waitForProjectToBeDeleted(GitLabApi gitLabApi, String projectPath) throws InterruptedException, GitLabApiException {
		int countDown = 30;
		Project project = null;
		do {
			Thread.sleep(1000L);
			project = gitLabApi.getProjectApi().getProject(projectPath);
		} while (project != null && countDown-- > 0);
		if (project != null) {
			throw new IllegalStateException("Project can not be deleted");
		}
	}

	private static LoggedRequest waitForRequest(WireMockServer wireMockServer) throws InterruptedException {
		int countDown = 30;
		List<ServeEvent> allServeEvents = List.of();
		do {
			Thread.sleep(1000L);
			allServeEvents = wireMockServer.getAllServeEvents();
		} while (allServeEvents.isEmpty() && countDown-- > 0);
		if (allServeEvents.size() != 1) {
			throw new IllegalStateException("Expecting 1 request, got: " + allServeEvents.size());
		}
		return allServeEvents.get(0).getRequest();
	}

	private static void createFile(GitLabApi gitLabApi, Project project, String branchName, String filePath, String content, String commitMessage) throws GitLabApiException {
		RepositoryFile file = new RepositoryFile();
		file.setContent(content);
		file.setFilePath(filePath);
		gitLabApi.getRepositoryFileApi().createFile(project, file, branchName, commitMessage);
	}

	private static void createAndCloseMergeRequest(GitLabApi gitLabApi, Project project, String sourceBranch, String targetBranch, String title) throws GitLabApiException {
		MergeRequest mr = createMergeRequest(gitLabApi, project, sourceBranch, targetBranch, title);

		MergeRequestParams closeParams = new MergeRequestParams()
				.withStateEvent(StateEvent.CLOSE);
		gitLabApi.getMergeRequestApi().updateMergeRequest(project, mr.getIid(), closeParams);

	}

	private static MergeRequest createMergeRequest(GitLabApi gitLabApi, Project project, String sourceBranch, String targetBranch, String title) throws GitLabApiException {
		MergeRequestParams mrParams = new MergeRequestParams()
				.withSourceBranch(sourceBranch)
				.withTargetBranch(targetBranch)
				.withTitle(title)
				.withDescription("A dummy pr")
				.withRemoveSourceBranch(true);
		return gitLabApi.getMergeRequestApi().createMergeRequest(project, mrParams);
	}

	private static Branch createBranch(GitLabApi gitLabApi, Project project, String branchName, String ref) throws GitLabApiException {
		Branch branch;
		try {
			branch = gitLabApi.getRepositoryApi().getBranch(project, branchName);
		} catch (GitLabApiException e) {
			branch = gitLabApi.getRepositoryApi().createBranch(project, branchName, ref);
		}
		return branch;
	}

	private static void deleteBranch(GitLabApi gitLabApi, Project project, String branchName) throws GitLabApiException {
		try {
			gitLabApi.getRepositoryApi().deleteBranch(project, branchName);
		} catch (GitLabApiException e) {
			//Nothing to do
		}
	}

	private static String ensureFileContent(GitLabApi gitLabApi, Project project, String commitSha, String expectedContent, String filePath) throws GitLabApiException {
		String fileContent;
		try {
			RepositoryFile ucascadeFile = gitLabApi.getRepositoryFileApi().getFile(project, filePath, commitSha);
			fileContent = new String(Base64.getDecoder().decode(ucascadeFile.getContent()));
		} catch (GitLabApiException e) {
			fileContent = null;
		}
		if (fileContent == null || !Objects.equals(fileContent, expectedContent)) {
			RepositoryFile file = new RepositoryFile();
			file.setContent(expectedContent);
			file.setFilePath(filePath);
			if (fileContent == null) {
				gitLabApi.getRepositoryFileApi().createFile(project, file, "main", "Create '" + filePath + "' file");
				//the response does not contains the commit ref, query the branch again
				Branch branch = gitLabApi.getRepositoryApi().getBranch(project, "main");
				return branch.getCommit().getId();
			} else {
				RepositoryFile f = gitLabApi.getRepositoryFileApi().updateFile(project, file, "main", "Update '" + file + "' file");
				return f.getRef();
			}
		}
		return commitSha;
	}

	private static JsonNode storeGetRequestResponse(Path file, String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.header("PRIVATE-TOKEN", TOKEN)
				.uri(new URI(url))
				.GET()
				.build();

		return storeRequestResponse(file, url, "get", request);
	}

	private static JsonNode storePostEmptyBodyRequestResponse(Path file, String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.header("PRIVATE-TOKEN", TOKEN)
				.uri(new URI(url))
				.POST(BodyPublishers.noBody())
				.build();

		return storeRequestResponse(file, url, "post", request);
	}

	private static JsonNode storePostRequestResponse(Path file, String url, String body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.header("Content-Type", "application/json")
				.header("PRIVATE-TOKEN", TOKEN)
				.uri(new URI(url))
				.POST(BodyPublishers.ofString(body))
				.build();

		return storeRequestResponse(file, url, "post", request);
	}

	private static JsonNode storePutRequestResponse(Path file, String url, String body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.header("Content-Type", "application/json")
				.header("PRIVATE-TOKEN", TOKEN)
				.uri(new URI(url))
				.PUT(BodyPublishers.ofString(body))
				.build();

		return storeRequestResponse(file, url, "post", request);
	}

	private static JsonNode storeRequestResponse(Path file, String url, String method, HttpRequest request) throws Exception {
		HttpClient client = HttpClient.newHttpClient();

		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

		String content = response.body();
		return storeJsonFile(file, content);
	}

	private static void storeRequest(Path file, LoggedRequest request) throws IOException {
		storeJsonFile(file, request.getBodyAsString());
	}

	private static JsonNode storeJsonFile(Path file, String content) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode jsonNode = mapper.readTree(content);
		String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
		json = json.replaceAll("\"project_id\" : \\d+,", "\"project_id\" : 1,");
		json = json.replaceAll("\"source_project_id\" : \\d+,", "\"source_project_id\" : 1,");
		json = json.replaceAll("\"target_project_id\" : \\d+,", "\"target_project_id\" : 1,");
		json = json.replaceAll("\"id\" : \\d+,", "\"id\" : 1,");
		json = json.replaceAll("\"merge_commit_sha\" : \"[^\"]+\",", "\"merge_commit_sha\" : \"e819d39ed37c6d4b8e700b9e7f34c74c099c163b\",");
		Files.writeString(file, json);
		return jsonNode;
	}
}
