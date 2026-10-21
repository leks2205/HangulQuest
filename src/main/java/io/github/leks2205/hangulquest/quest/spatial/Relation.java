package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where something is, relative to an anchor. Each relation is one Korean location noun (위, 옆, 안 ...)
 * and carries the TOPIK grammar point it practises.
 */
public enum Relation {
	ON_TOP_OF("위", "on top", "oben drauf", "wi", 1, false),
	UNDER("아래", "under", "darunter", "arae", 1, false),
	NEXT_TO("옆", "next to", "daneben", "yeop", 1, false),
	IN_FRONT_OF("앞", "in front of", "davor", "ap", 1, true),
	BEHIND("뒤", "behind", "dahinter", "dwi", 1, true),
	INSIDE("안", "inside", "drinnen", "an", 1, false),
	OUTSIDE("밖", "outside", "draußen", "bak", 1, false),
	BETWEEN("사이", "between", "dazwischen", "sai", 2, false),
	NEAR("근처", "near", "in der Nähe", null, 2, false);

	private final String ko;
	private final String en;
	private final String de;
	private final String slug;
	private final int grammarLevel;
	private final boolean needsFacing;

	Relation(String ko, String en, String de, String slug, int grammarLevel, boolean needsFacing) {
		this.ko = ko;
		this.en = en;
		this.de = de;
		this.slug = slug;
		this.grammarLevel = grammarLevel;
		this.needsFacing = needsFacing;
	}

	/** The location noun as it stands in the sentence: 위, 옆, 사이. */
	public String ko() {
		return ko;
	}

	/** The meaning in the interface language, for the "wrong relation" status line. */
	public String gloss(Lang lang) {
		return lang == Lang.DE ? de : en;
	}

	/** True for 앞 and 뒤: they only make sense for anchors that face somewhere. */
	public boolean needsFacing() {
		return needsFacing;
	}

	/**
	 * The grammar-point id of this location noun with 에, e.g. {@code L1.loc_wi_e} (위에) or
	 * {@code L2.loc_sai_e} (사이에); null for 근처, which has no entry in grammar.json.
	 */
	public String grammarId() {
		return slug == null ? null : (grammarLevel == 1 ? "L1.loc_" : "L2.loc_") + slug + "_e";
	}

	/** The ids a phrase with this place word practises: the noun, plus 에서 when the role is an activity place. */
	public List<String> grammarIds(Role role) {
		List<String> ids = new ArrayList<>(2);
		if (grammarId() != null) {
			ids.add(grammarId());
		}
		if (role == Role.ACTIVITY_PLACE) {
			ids.add("L2.eseo_activity");
		}
		return ids;
	}

	public static Relation byId(String id, Relation fallback) {
		if (id == null) {
			return fallback;
		}
		try {
			return valueOf(id.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			return fallback;
		}
	}
}
