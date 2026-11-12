package com.ascenddevs.safetyapp.service;

import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppFormTest {

    @Test
    void withoutTemplateTheTextGoesInBody() {
        MultiValueMap<String, String> f = WhatsAppSender.buildForm("whatsapp:+1", "+9111", "hello", List.of("a"), "", 0);
        assertEquals("hello", f.getFirst("Body"));
        assertNull(f.getFirst("ContentSid"));
        assertEquals("whatsapp:+9111", f.getFirst("To"));
    }

    @Test
    void fixedTemplateSendsOnlyTheContentSid() {
        MultiValueMap<String, String> f = WhatsAppSender.buildForm("whatsapp:+1", "+9111", "hello", List.of("a"), "HX123", 0);
        assertEquals("HX123", f.getFirst("ContentSid"));
        assertNull(f.getFirst("Body"));
        assertNull(f.getFirst("ContentVariables"));
    }

    @Test
    void templateWithBlanksGetsVariablesAndEscapedText() {
        MultiValueMap<String, String> f = WhatsAppSender.buildForm("whatsapp:+1", "+9111", "x",
                List.of("Asha \"A\"", "10 Nov", "https://maps"), "HX123", 3);
        assertEquals("{\"1\":\"Asha \\\"A\\\"\",\"2\":\"10 Nov\",\"3\":\"https://maps\"}", f.getFirst("ContentVariables"));
    }
}
