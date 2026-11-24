package com.aibeauty.api.ingredient.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** 실제 API를 부르지 않고, 확인해 둔 응답 형태로 파싱만 검증한다. */
class MfdsIngredientClientTest {

	private final MfdsIngredientClient client = new MfdsIngredientClient(
			new MfdsIngredientProperties("test-key", null, 0, null, null), JsonMapper.builder().build());

	@Test
	@DisplayName("정상 응답에서 전체 건수와 성분 목록을 읽는다")
	void parsesSuccess() {
		String json = """
				{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
				 "body":{"pageNo":1,"totalCount":21968,"numOfRows":2,"items":[
				   {"INGR_KOR_NAME":"가공소금","INGR_ENG_NAME":null,"CAS_NO":null,"ORIGIN_MAJOR_KOR_NAME":null,"INGR_SYNONYM":null},
				   {"INGR_KOR_NAME":"가지열매추출물","INGR_ENG_NAME":"Solanum Melongena (Eggplant) Fruit Extract",
				    "CAS_NO":"84012-19-1","ORIGIN_MAJOR_KOR_NAME":"이 원료는 가지의 열매에서 추출한 것이다.","INGR_SYNONYM":"가지추출물"}]}}
				""";

		MfdsIngredientClient.Page page = client.parse(1, json);

		assertThat(page.totalCount()).isEqualTo(21968);
		assertThat(page.items()).hasSize(2);
		assertThat(page.items().get(1).synonyms()).isEqualTo("가지추출물");
		assertThat(page.rawJson()).isEqualTo(json);
	}

	@Test
	@DisplayName("결과 코드가 00이 아니면 메시지와 함께 실패한다")
	void failsOnErrorCode() {
		String json = """
				{"header":{"resultCode":"11","resultMsg":"numOfRows maximum is =[500]"},"body":{}}
				""";

		assertThatThrownBy(() -> client.parse(1, json))
				.isInstanceOf(MfdsIngredientClient.MfdsApiException.class)
				.hasMessageContaining("11")
				.hasMessageContaining("maximum");
	}

	@Test
	@DisplayName("인증 오류처럼 JSON이 아닌 응답은 재시도하지 않고 실패한다")
	void failsOnNonJson() {
		String xml = "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
				+ "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>";

		assertThatThrownBy(() -> client.parse(1, xml))
				.isInstanceOfSatisfying(MfdsIngredientClient.MfdsApiException.class,
						e -> assertThat(e.retryable()).isFalse())
				.hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
	}
}
