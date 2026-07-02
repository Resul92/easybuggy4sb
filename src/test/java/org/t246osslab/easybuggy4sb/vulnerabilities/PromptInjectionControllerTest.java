package org.t246osslab.easybuggy4sb.vulnerabilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

public class PromptInjectionControllerTest {

    @Test
    public void constructorKeepsSingleReusableRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        PromptInjectionController controller = new PromptInjectionController(restTemplate);

        assertSame(restTemplate, ReflectionTestUtils.getField(controller, "restTemplate"));
    }

    @Test
    public void defaultRestTemplateUsesConnectAndReadTimeouts() {
        PromptInjectionController controller = new PromptInjectionController();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(controller, "restTemplate");
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();

        assertTrue(requestFactory instanceof SimpleClientHttpRequestFactory);
        assertEquals(5000, ReflectionTestUtils.getField(requestFactory, "connectTimeout"));
        assertEquals(60000, ReflectionTestUtils.getField(requestFactory, "readTimeout"));
    }
}
