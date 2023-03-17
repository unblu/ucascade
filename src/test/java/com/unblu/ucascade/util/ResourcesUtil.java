package com.unblu.ucascade.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import com.unblu.ucascade.WireMockGitlabProxy;

public class ResourcesUtil {

	public static String readFromResources(String name) {
		try (InputStream is = WireMockGitlabProxy.class.getResourceAsStream(name)) {
			return new BufferedReader(new InputStreamReader(is))
					.lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			throw new RuntimeException("Could not read resource " + name, e);
		}
	}

}
