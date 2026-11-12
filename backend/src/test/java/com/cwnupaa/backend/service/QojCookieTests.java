package com.cwnupaa.backend.service;

import com.cwnupaa.backend.utils.QojCookie;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class QojCookieTests {
    @Test void normalizesFullHeaderAndPreservesHardenedNamesAndPaddedValues() {
        assertThat(QojCookie.normalize("Cookie: __Host-UOJSESSID = private==; future_name=v; "))
                .isEqualTo("__Host-UOJSESSID=private==; future_name=v");
        assertThat(QojCookie.normalize("cookie: a=one=two; b=" )).isEqualTo("a=one=two; b=");
    }
    @Test void malformedHeadersNeverReachAnUpstreamRequestOrAppearInErrors() {
        for (String value : new String[]{"private-marker", "=private-marker", "bad name=private-marker", "a=ok; bad", "a=private-marker\r\nX-Test: 1", "a=private-marker\n", "; ;", "a=含中文"}) {
            assertThatThrownBy(() -> QojCookie.normalize(value)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("格式不正确").hasMessageNotContaining("private-marker");
        }
    }
}
