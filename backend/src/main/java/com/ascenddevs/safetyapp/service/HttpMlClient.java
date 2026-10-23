package com.ascenddevs.safetyapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls the FastAPI service's audio prediction endpoints, uploading the WAV as a multipart "file" part. */
@Component
public class HttpMlClient implements MlClient {

    private final RestClient restClient;

    public HttpMlClient(@Value("${ml-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public DistressResult predictDistress(byte[] wavAudio) {
        return post("/predict/distress/audio", wavAudio, DistressResult.class);
    }

    @Override
    public ToneResult predictTone(byte[] wavAudio) {
        return post("/predict/tone/audio", wavAudio, ToneResult.class);
    }

    private <T> T post(String path, byte[] wavAudio, Class<T> responseType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(wavAudio) {
            @Override
            public String getFilename() {
                return "clip.wav"; // a filename is what makes this a file part rather than a form field
            }
        });
        try {
            T result = restClient.post()
                    .uri(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (result == null) {
                throw new MlServiceException("Empty response from ML service at " + path);
            }
            return result;
        } catch (RestClientException e) {
            throw new MlServiceException("ML service call to " + path + " failed: " + e.getMessage(), e);
        }
    }
}
