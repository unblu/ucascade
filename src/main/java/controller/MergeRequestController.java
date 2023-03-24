package controller;

import java.util.Random;

import javax.inject.Inject;

import org.gitlab4j.api.webhook.MergeRequestEvent;

import controller.model.CascadeResult;
import controller.model.MergeRequestSimple;
import io.quarkus.logging.Log;
import io.quarkus.vertx.web.Body;
import io.quarkus.vertx.web.Header;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HandlerType;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.quarkus.vertx.web.RouteBase;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.eventbus.EventBus;
import service.GitLabService;
import util.JsonUtils;

@RouteBase(produces = "application/json")
public class MergeRequestController {

	@Inject
	GitLabService gitLabService;

	@Inject
	EventBus eventsBus;

	@Route(path = "/ucascade/merge-request", order = 1, methods = HttpMethod.POST, type = HandlerType.NORMAL)
	public CascadeResult mergeRequest(@Header("X-Gitlab-Event-UUID") String gitlabEventUUID, @Body MergeRequestEvent mrEvent, HttpServerResponse response) {
		Log.infof("GitlabEvent: '%s' | Received", gitlabEventUUID);
		MergeRequestSimple simpleEvent = JsonUtils.toSimpleEvent(mrEvent, gitlabEventUUID);
		// consumed by GitLabService class
		eventsBus.send(GitLabService.MERGE_REQUEST_EVENT, simpleEvent);
		response.setStatusCode(202);
		return gitLabService.createResult(gitlabEventUUID);
	}

	@Route(path = "/ucascade/merge-request-blocking", order = 1, methods = HttpMethod.POST, type = HandlerType.BLOCKING)
	public CascadeResult mergeRequestBlocking(@Header("X-Gitlab-Event-UUID") String gitlabEventUUID, @Body MergeRequestEvent mrEvent) {
		Log.infof("GitlabEvent: '%s' | Received (blocking)", gitlabEventUUID);
		MergeRequestSimple simpleEvent = JsonUtils.toSimpleEvent(mrEvent, gitlabEventUUID);
		return gitLabService.mergeRequest(simpleEvent);
	}

	@Route(path = "/ucascade/replay", order = 1, methods = HttpMethod.POST, type = HandlerType.BLOCKING)
	public CascadeResult replay(@Body MergeRequestSimple mrSimple) {
		String gitlabEventUUID = mrSimple.getGitlabEventUUID();
		if (gitlabEventUUID == null) {
			gitlabEventUUID = "replay-" + new Random().nextInt(1000, 10000);
		}
		Log.infof("GitlabEvent: '%s' | Replay", gitlabEventUUID);
		return gitLabService.mergeRequest(mrSimple);
	}

	@Route(path = "/*", order = 2)
	public void other(@Header("X-Gitlab-Event-UUID") String gitlabEventUUID, RoutingContext rc) {
		String path = rc.request().path();
		if (path.equals("/q/health/live") || path.equals("/q/health/ready")) {
			// the module 'quarkus-smallrye-health' will answer:
			rc.next();
		} else {
			Log.infof("GitlabEvent: '%s' | Invalid path '%s' ", gitlabEventUUID, path);

			CascadeResult result = gitLabService.createResult(gitlabEventUUID);
			result.setError("Invalid path: " + path);
			String body = Json.encode(result);
			rc.response()
					.setStatusCode(202)
					.end(body);
		}
	}

	@Route(path = "/*", order = 3, type = HandlerType.FAILURE)
	public CascadeResult error(@Header("X-Gitlab-Event-UUID") String gitlabEventUUID, RoutingContext rc) {
		Throwable t = rc.failure();
		Log.warnf(t, "GitlabEvent: '%s' | Failed to handle request on path '%s' ", gitlabEventUUID, rc.request().path());

		CascadeResult result = gitLabService.createResult(gitlabEventUUID);
		if (t != null) {
			result.setError(t.getMessage());
		} else {
			result.setError("Unknown error");
		}
		rc.response().setStatusCode(202);
		return result;
	}
}
