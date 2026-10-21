package io.github.leks2205.hangulquest.quest.grammar;

import io.github.leks2205.hangulquest.quest.Verb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verb used to store its stems and 아/어 forms by hand. This table is that hand-stored data, frozen
 * (name, dictionary, stem, eoyo, future, attributive, honorific, polite, past), and every form that
 * Verb now gets from the Conjugator has to equal it.
 */
class VerbParityTest {
	private static final String[] OLD = {
		"MINE|캐다|캐|캐|캘|캔|캐세요|캐요|캤어요",
		"CHOP|베다|베|베어|벨|벤|베세요|베어요|베었어요",
		"HARVEST|수확하다|수확하|수확해|수확할|수확한|수확하세요|수확해요|수확했어요",
		"PLACE|놓다|놓|놓아|놓을|놓은|놓으세요|놓아요|놓았어요",
		"COLLECT|모으다|모으|모아|모을|모은|모으세요|모아요|모았어요",
		"EAT|먹다|먹|먹어|먹을|먹은|먹으세요|먹어요|먹었어요",
		"DRINK|마시다|마시|마셔|마실|마신|마시세요|마셔요|마셨어요",
		"LOOK|보다|보|봐|볼|본|보세요|봐요|봤어요",
		"WATCH|바라보다|바라보|바라봐|바라볼|바라본|바라보세요|바라봐요|바라봤어요",
		"DEFEAT|물리치다|물리치|물리쳐|물리칠|물리친|물리치세요|물리쳐요|물리쳤어요",
		"GO|가다|가|가|갈|간|가세요|가요|갔어요",
		"CLIMB|올라가다|올라가|올라가|올라갈|올라간|올라가세요|올라가요|올라갔어요",
		"DESCEND|내려가다|내려가|내려가|내려갈|내려간|내려가세요|내려가요|내려갔어요",
		"OPEN|열다|열|열어|열|연|여세요|열어요|열었어요",
		"TALK|이야기하다|이야기하|이야기해|이야기할|이야기한|이야기하세요|이야기해요|이야기했어요",
		"SLEEP|자다|자|자|잘|잔|자세요|자요|잤어요",
		"CRAFT|만들다|만들|만들어|만들|만든|만드세요|만들어요|만들었어요",
		"USE|쓰다|쓰|써|쓸|쓴|쓰세요|써요|썼어요",
		"RING|울리다|울리|울려|울릴|울린|울리세요|울려요|울렸어요",
		"BREAK|부수다|부수|부숴|부술|부순|부수세요|부숴요|부쉈어요",
		"DIG|파다|파|파|팔|판|파세요|파요|팠어요",
		"PULL|뽑다|뽑|뽑아|뽑을|뽑은|뽑으세요|뽑아요|뽑았어요",
		"SNAP|꺾다|꺾|꺾어|꺾을|꺾은|꺾으세요|꺾어요|꺾었어요",
		"PICK|따다|따|따|딸|딴|따세요|따요|땄어요",
		"SHATTER|깨다|깨|깨|깰|깬|깨세요|깨요|깼어요",
		"PLANT|심다|심|심어|심을|심은|심으세요|심어요|심었어요",
		"SPREAD|깔다|깔|깔아|깔|깐|까세요|깔아요|깔았어요",
		"HANG|걸다|걸|걸어|걸|건|거세요|걸어요|걸었어요",
		"STAND|세우다|세우|세워|세울|세운|세우세요|세워요|세웠어요",
		"HANG_DOOR|달다|달|달아|달|단|다세요|달아요|달았어요",
		"STACK|쌓다|쌓|쌓아|쌓을|쌓은|쌓으세요|쌓아요|쌓았어요",
		"LIGHT|밝히다|밝히|밝혀|밝힐|밝힌|밝히세요|밝혀요|밝혔어요",
		"KINDLE|피우다|피우|피워|피울|피운|피우세요|피워요|피웠어요",
		"OBTAIN|구하다|구하|구해|구할|구한|구하세요|구해요|구했어요",
		"PICK_UP|줍다|줍|주워|주울|주운|주우세요|주워요|주웠어요",
		"WEAVE|치다|치|쳐|칠|친|치세요|쳐요|쳤어요",
		"PRESS|누르다|누르|눌러|누를|누른|누르세요|눌러요|눌렀어요",
		"FLIP|당기다|당기|당겨|당길|당긴|당기세요|당겨요|당겼어요",
		"SWITCH_ON|켜다|켜|켜|켤|켠|켜세요|켜요|켰어요",
		"ARRIVE|도착하다|도착하|도착해|도착할|도착한|도착하세요|도착해요|도착했어요",
		"FEED|주다|주|줘|줄|준|주세요|줘요|줬어요",
		"THROW|던지다|던지|던져|던질|던진|던지세요|던져요|던졌어요",
		"PET|쓰다듬다|쓰다듬|쓰다듬어|쓰다듬을|쓰다듬은|쓰다듬으세요|쓰다듬어요|쓰다듬었어요",
		"RIDE|타다|타|타|탈|탄|타세요|타요|탔어요",
		"ADDRESS|걸다|걸|걸어|걸|건|거세요|걸어요|걸었어요",
		"TOUCH|만지다|만지|만져|만질|만진|만지세요|만져요|만졌어요",
		"GIVE_ITEM|주다|주|줘|줄|준|주세요|줘요|줬어요",
		"GIVE|주다|주|줘|줄|준|주세요|줘요|줬어요",
		"PUT_IN|넣다|넣|넣어|넣을|넣은|넣으세요|넣어요|넣었어요",
		"HOLD|들다|들|들어|들|든|드세요|들어요|들었어요",
		"ENTER|들어가다|들어가|들어가|들어갈|들어간|들어가세요|들어가요|들어갔어요",
		"DROP_AT|떨어뜨리다|떨어뜨리|떨어뜨려|떨어뜨릴|떨어뜨린|떨어뜨리세요|떨어뜨려요|떨어뜨렸어요",
		"CLOSE|닫다|닫|닫아|닫을|닫은|닫으세요|닫아요|닫았어요",
		"MELT|녹이다|녹이|녹여|녹일|녹인|녹이세요|녹여요|녹였어요",
		"BAKE|굽다|굽|구워|구울|구운|구우세요|구워요|구웠어요",
		"WALK|걷다|걷|걸어|걸을|걸은|걸으세요|걸어요|걸었어요",
	};

	@Test
	void everyVerbIsInTheFrozenTable() {
		assertEquals(Verb.values().length, OLD.length);
		for (int i = 0; i < OLD.length; i++) {
			assertEquals(Verb.values()[i].name(), OLD[i].split("\\|")[0], "row " + i);
		}
	}

	@Test
	void conjugatorFormsEqualTheOldStoredForms() {
		for (String row : OLD) {
			String[] cells = row.split("\\|");
			Verb verb = Verb.valueOf(cells[0]);
			assertEquals(cells[1], verb.dictionary(), cells[0] + " dictionary");
			assertEquals(cells[2], verb.stem(), cells[0] + " stem");
			assertEquals(cells[3], verb.eoyo(), cells[0] + " eoyo");
			assertEquals(cells[4], verb.future(), cells[0] + " future");
			assertEquals(cells[5], verb.attributive(), cells[0] + " attributive");
			assertEquals(cells[6], verb.honorific(), cells[0] + " honorific");
			assertEquals(cells[7], verb.polite(), cells[0] + " polite");
			assertEquals(cells[8], verb.past(), cells[0] + " past");
		}
	}
}
