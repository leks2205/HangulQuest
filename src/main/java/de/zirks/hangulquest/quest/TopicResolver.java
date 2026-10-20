package de.zirks.hangulquest.quest;

/** Normalises authored/external topic values to the branch topics used by icons and auto-continue. */
public final class TopicResolver {
	private TopicResolver() {
	}

	public static String keyFor(Questline trail, java.util.Map<String, Questline> byId) {
		String direct = key(trail.topic());
		if (!"기타".equals(direct)) {
			return direct;
		}
		if (trail.topic() != null && !trail.topic().isBlank()) {
			// An authored topic outside the known set stays distinct instead of collapsing to one bucket.
			return trail.topic().trim();
		}
		String id = trail.id();
		java.util.Set<String> seen = new java.util.HashSet<>();
		while (seen.add(id)) {
			Questline current = byId.get(id);
			if (current == null || current.requires().completed().isEmpty()) {
				return rootTopic(id);
			}
			id = current.requires().completed().get(0);
		}
		return "기타";
	}

	public static String rootTopic(String id) {
		if (id == null) {
			return "기타";
		}
		return switch (id) {
			case "steinpfad", "steintiefe", "kohlenpfad", "bergwerk" -> "돌";
			case "holzpfad", "waldhaus", "gartenpfad", "weltbaum" -> "나무";
			case "essenspfad", "baeckerei", "brotpfad", "festmahl" -> "음식";
			case "tierpfad", "farmpfad", "haustierpfad" -> "동물";
			case "wasserpfad", "fischerpfad", "brandung" -> "물";
			case "nachtpfad", "sternenpfad", "nachtwache" -> "밤";
			case "bergpfad", "gipfelpfad" -> "산";
			case "dorfpfad", "handelspfad", "marktpfad" -> "마을";
			case "feuerpfad", "schmiedepfad" -> "불";
			default -> "기타";
		};
	}

	public static String key(String topic) {
		if (topic == null || topic.isBlank()) {
			return "기타";
		}
		String t = topic.trim().toLowerCase(java.util.Locale.ROOT);
		for (String known : new String[]{"돌", "나무", "음식", "물", "밤", "산", "마을", "가족", "불", "동물"}) {
			if (t.equals(known)) {
				return known;
			}
		}
		if (t.contains("stein") || t.contains("돌")) return "돌";
		if (t.contains("holz") || t.contains("나무")) return "나무";
		if (t.contains("essen") || t.contains("brot") || t.contains("음식")) return "음식";
		if (t.contains("wasser") || t.contains("물")) return "물";
		if (t.contains("nacht") || t.contains("밤")) return "밤";
		if (t.contains("berg") || t.contains("산")) return "산";
		if (t.contains("dorf") || t.contains("마을")) return "마을";
		if (t.contains("familie") || t.contains("가족")) return "가족";
		if (t.contains("feuer") || t.contains("불")) return "불";
		if (t.contains("tier") || t.contains("동물")) return "동물";
		return "기타";
	}
}
