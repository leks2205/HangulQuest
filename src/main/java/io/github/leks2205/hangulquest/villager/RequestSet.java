package io.github.leks2205.hangulquest.villager;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The requests that passed validation, by id. */
public final class RequestSet {
	public static final RequestSet EMPTY = new RequestSet(Map.of());

	private final Map<String, Request> byId;

	private RequestSet(Map<String, Request> byId) {
		this.byId = byId;
	}

	public static RequestSet of(Collection<Request> requests) {
		Map<String, Request> sorted = new TreeMap<>();
		requests.forEach(request -> sorted.put(request.id(), request));
		return new RequestSet(Map.copyOf(sorted));
	}

	/** Parses and validates the files (file id to bytes); a request with problems is left out and logged. */
	public static RequestSet load(Map<String, byte[]> files, RequestValidator.Env env, Logger log) {
		List<Request> good = new ArrayList<>();
		for (Map.Entry<String, byte[]> file : files.entrySet()) {
			RequestParser.Parsed parsed = RequestParser.parse(file.getKey(), file.getValue());
			List<String> problems = new ArrayList<>(parsed.problems());
			if (parsed.request() != null && problems.isEmpty()) {
				problems.addAll(RequestValidator.validate(parsed.request(), env));
			}
			if (problems.isEmpty()) {
				good.add(parsed.request());
			} else {
				log.warn("request {} not loaded: {}", file.getKey(), String.join("; ", problems));
			}
		}
		return of(good);
	}

	public Request get(String id) {
		return id == null ? null : byId.get(id);
	}

	/** Every request, ordered by id. */
	public Collection<Request> all() {
		return new TreeMap<>(byId).values();
	}

	public int size() {
		return byId.size();
	}
}
