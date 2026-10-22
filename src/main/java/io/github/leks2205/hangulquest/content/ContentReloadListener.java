package io.github.leks2205.hangulquest.content;

import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.slf4j.Logger;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Loads the mod's content with every resource reload (start-up and F3+T): the prepare phase runs
 * on a background thread, the apply phase on the main thread. Resource packs take part because
 * everything is read through the resource manager.
 */
public final class ContentReloadListener extends SimpleReloadListener<Content> {
	private final Supplier<ContentLoader.Inputs> inputs;
	private final Consumer<Content> sink;
	private final Logger log;

	public ContentReloadListener(Supplier<ContentLoader.Inputs> inputs, Consumer<Content> sink, Logger log) {
		this.inputs = inputs;
		this.sink = sink;
		this.log = log;
	}

	@Override
	protected Content prepare(PreparableReloadListener.SharedState state) {
		return ContentLoader.prepare(state.resourceManager(), inputs.get(), log);
	}

	@Override
	protected void apply(Content content, PreparableReloadListener.SharedState state) {
		try {
			sink.accept(content);
		} catch (Throwable failed) {
			// A failing listener would abort the whole resource reload; the sink has its own retry.
			log.error("could not apply hangulquest content", failed);
		}
	}
}
