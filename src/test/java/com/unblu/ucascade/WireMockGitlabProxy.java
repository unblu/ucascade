package com.unblu.ucascade;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.util.Collections;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class WireMockGitlabProxy implements QuarkusTestResourceLifecycleManager {

	final static String API_PREFIX = "/api/v4/";

	WireMockServer wireMockServer;

	@Override
	public Map<String, String> start() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();

		System.out.println("WireMock server: " + wireMockServer.baseUrl());
		return Collections.singletonMap("gitlab.host", wireMockServer.baseUrl());
	}

	@Override
	public synchronized void stop() {
		if (wireMockServer != null) {
			wireMockServer.stop();
			wireMockServer = null;
		}
	}

	@Override
	public void inject(TestInjector testInjector) {
		testInjector.injectIntoFields(
				wireMockServer,
				new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
	}

}
