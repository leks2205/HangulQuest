package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.ConversationKind;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.Speaker;
import io.github.leks2205.hangulquest.dialogue.SpeechLevel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The conversations a request is spoken through. Their id is the request id and their node ids are
 * {@code <personality>_<part>}, so the glosses ({@code hangulquest.dialogue.<id>.<node>}) and the recorded lines
 * ({@code line.<id>.<node>.<voice>}) are found like those of any dialogue.
 */
public final class RequestDialogues {
	private RequestDialogues() {
	}

	/** The offer, then the player's yes or no and the villager's answer to it. The offer exposes the words. */
	public static Dialogue offer(Request request, Personality personality) {
		String offer = Request.nodeId(personality, Request.Part.OFFER);
		String accept = Request.nodeId(personality, Request.Part.ACCEPT);
		String decline = Request.nodeId(personality, Request.Part.DECLINE);
		List<Dialogue.Choice> answers = List.of(
				new Dialogue.Choice(request.yes().ko(), SpeechLevel.HAEYO, accept, null,
						List.of(new Dialogue.AcceptRequest(request.id())), request.answerKey(true)),
				new Dialogue.Choice(request.no().ko(), SpeechLevel.HAEYO, decline, null,
						List.of(new Dialogue.DeclineRequest(request.id())), request.answerKey(false)));
		Map<String, Dialogue.Node> nodes = new LinkedHashMap<>();
		List<Dialogue.Effect> expose = request.words().isEmpty() ? List.of()
				: List.of(new Dialogue.ExposeWords(request.words()));
		nodes.put(offer, node(request, personality, Request.Part.OFFER, null, answers, expose, false));
		nodes.put(accept, node(request, personality, Request.Part.ACCEPT, null, List.of(), List.of(), true));
		nodes.put(decline, node(request, personality, Request.Part.DECLINE, null, List.of(), List.of(), true));
		return new Dialogue(request.id(), request.level(), trigger(request), offer, nodes);
	}

	/** One line: the reminder of an open request, or the thanks when it is handed in. */
	public static Dialogue single(Request request, Personality personality, Request.Part part) {
		String id = Request.nodeId(personality, part);
		return new Dialogue(request.id(), request.level(), trigger(request), id,
				Map.of(id, node(request, personality, part, null, List.of(), List.of(), true)));
	}

	private static Dialogue.Trigger trigger(Request request) {
		return new Dialogue.Trigger("minecraft:villager", null, false, request.professions(),
				request.offeredBy(), request.regions(), ConversationKind.REQUEST, 0);
	}

	private static Dialogue.Node node(Request request, Personality personality, Request.Part part, String next,
									  List<Dialogue.Choice> choices, List<Dialogue.Effect> effects, boolean end) {
		Request.Line line = request.line(personality, part);
		return new Dialogue.Node(Request.nodeId(personality, part), Speaker.VILLAGER, personality.register(),
				line == null ? "" : line.ko(), next, choices, null, effects, end && choices.isEmpty(), null,
				line == null ? null : line.koBanmal(), false);
	}
}
