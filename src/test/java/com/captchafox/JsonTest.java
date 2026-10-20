package com.captchafox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {

    @Test
    void readsEveryKindOfValue() {
        Object value = Json.parse(" {\"a\" : [0, -2.5e3, true, false, null, \"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u00e4\"], \"b\":{}} ");

        List<Object> a = Arrays.asList(new BigDecimal("0"), new BigDecimal("-2.5e3"), true, false, null, "\"\\/\b\f\n\r\tä");
        assertEquals(Map.of("a", a, "b", Map.of()), value);
    }

    @Test
    void readsAnEscapedName() {
        assertEquals(Map.of("success", true), Json.parse("{\"suc\\u0063ess\":true}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", " ", "{", "{\"a\"}", "{\"a\":1,}", "[1,]", "[1 2]", "01", "1.", "1e", "-", ".5", "+1",
        "\"\u0001\"", "\"\\x\"", "\"\\u12\"", "\"\\u\uFF10\uFF10\uFF17\uFF13\"", "\"open", "tru", "nul", "{\"a\":1} x", "{'a':1}", "NaN",
    })
    void refusesWhatIsNoJson(String text) {
        assertThrows(IllegalArgumentException.class, () -> Json.parse(text));
    }
}
