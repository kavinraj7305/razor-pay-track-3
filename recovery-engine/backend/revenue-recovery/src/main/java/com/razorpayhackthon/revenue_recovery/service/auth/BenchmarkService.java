package com.razorpayhackthon.revenue_recovery.service.auth;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class BenchmarkService {

	static final String RESOURCE = "benchmark/acc_syn_training_500.json";

	private final JsonMapper jsonMapper;

	public BenchmarkService(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public Map<String, Object> latest() {
		ClassPathResource resource = new ClassPathResource(RESOURCE);
		if (!resource.exists()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "benchmark file is missing");
		}
		try (InputStream input = resource.getInputStream()) {
			return jsonMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
		} catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could not read benchmark");
		}
	}
}
