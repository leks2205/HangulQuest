package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.ExposureLedger;
import io.github.leks2205.hangulquest.learn.LearningSink;

/**
 * What reading a story does for the learner: every catalog word of the story is an encounter, never a
 * review, so the schedule stays as it is. A story counts once per study day, so reopening it does not
 * farm encounters. The context is {@code story} with the story id in the trail slot, so each story is a
 * context of its own for the recycling rule. A glossed word is taught the way a quest step teaches one
 * ({@link LearningSink#questStep}); the gloss was an aid, so it is never graded as a recall.
 */
public final class StoryReading {
	public static final String CONTEXT = "story";
	public static final String ROLE = "reading";
	/** The step name in the review log for a glossed word. */
	private static final String GLOSS_STEP = "gloss";

	private StoryReading() {
	}

	public static ExposureLedger.Context context(Story story) {
		return new ExposureLedger.Context(CONTEXT, "", ROLE, story.id());
	}

	/**
	 * Logs the encounters of one opening of the story and remembers the day. Returns how many words were
	 * exposed: 0 when the story was already read on this study day.
	 */
	public static int open(LearningSink sink, Catalog catalog, MarkFile readLog, Story story, long now, String dayKey) {
		if (readLog.is(story.id(), dayKey)) {
			return 0;
		}
		int exposed = 0;
		for (String id : story.words()) {
			Word word = catalog.byId(id).orElse(null);
			if (word != null) {
				if (story.glossed().contains(id)) {
					sink.questStep(word, now, true, false, context(story), GLOSS_STEP);
				} else {
					sink.expose(word, now, context(story));
				}
				exposed++;
			}
		}
		readLog.set(story.id(), dayKey);
		return exposed;
	}
}
