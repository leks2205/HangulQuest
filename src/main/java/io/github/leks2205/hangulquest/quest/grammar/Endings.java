package io.github.leks2205.hangulquest.quest.grammar;

import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator.Word;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The generator names of grammar.json ("ending:seyo", "connective:go", ...) mapped to {@link Conjugator}
 * calls. A generator turns a dictionary form into the construction's form: "먹다" through "nominal:eun_hue"
 * gives "먹은 후에". Only the verb-side constructions are here; particles, counters, numerals and
 * relations belong to nouns. {@code ending:ieyo} is the exception that takes a noun ("횃불" → "횃불이에요").
 */
public final class Endings {
	private static final Map<String, UnaryOperator<String>> GENERATORS = new LinkedHashMap<>();

	static {
		word("ending:eoyo", Word::eoyo);
		word("ending:past", Word::past);
		word("ending:seyo", Word::seyo);
		word("ending:future", Word::geoyeyo);
		word("ending:go_sipeo", Word::goSipeo);
		word("ending:su_itda", Word::suIsseoyo);
		word("ending:jima_seyo", Word::jimaseyo);
		word("ending:ji_anayo", Word::jiAnayo);
		word("ending:eoya_haeyo", Word::eoyaHaeyo);
		word("ending:eulgeyo", Word::eulgeyo);
		word("ending:myeon_an_doeyo", Word::myeonAnDoeyo);
		word("ending:hapsyo", Word::hapnida);
		word("ending:banmal", Word::banmal);
		word("ending:handa", Word::handa);
		GENERATORS.put("ending:ieyo", KoGrammar::ieyo);

		word("connective:go", Word::go);
		word("connective:go_manner", Word::go);
		word("connective:myeon", Word::myeon);
		word("connective:jiman", Word::jiman);
		word("connective:eoseo", Word::aseo);
		word("connective:eureo", Word::eureo);
		word("connective:eodo", Word::ado);
		word("connective:neunde", Word::neunde);
		word("connective:eunikka", Word::nikka);
		word("connective:eulsurok", Word::eulsurok);
		word("connective:deusi", Word::deusi);

		word("nominal:eun_hue", Word::eunHue);
		word("nominal:gi_jeone", Word::giJeone);
		word("nominal:gi_maryeon", Word::giMaryeon);
		word("nominal:neun_dongan", Word::neunDongan);
		word("nominal:su_bakke", Word::suBakke);

		word("adnominal:past", Word::eun);
		word("adnominal:present", Word::neun);
		word("adnominal:future", Word::future);
		word("adnominal:adjective", Word::eun);
	}

	private Endings() {
	}

	private static void word(String name, Function<Word, String> form) {
		GENERATORS.put(name, dictionary -> form.apply(Conjugator.of(dictionary)));
	}

	/** True when the generator name of a construction is known. */
	public static boolean has(String generator) {
		return GENERATORS.containsKey(generator);
	}

	public static Set<String> generators() {
		return Set.copyOf(GENERATORS.keySet());
	}

	/** The form for this generator and dictionary form; throws for an unknown generator. */
	public static String apply(String generator, String dictionary) {
		UnaryOperator<String> form = GENERATORS.get(generator);
		if (form == null) {
			throw new IllegalArgumentException("unknown grammar generator: " + generator);
		}
		return form.apply(dictionary);
	}
}
