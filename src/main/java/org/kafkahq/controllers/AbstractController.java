package org.kafkahq.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Wither;
import org.kafkahq.modules.KafkaModule;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

abstract public class AbstractController {
    private static final String SESSION_TOAST = "TOAST";
    private static Gson gson = new GsonBuilder()
        .enableComplexMapKeySerialization()
        .create();

    @Value("${kafkahq.server.base-path}")
    protected String basePath;

    @Inject
    private KafkaModule kafkaModule;

    @SuppressWarnings("unchecked")
    protected Map templateData(Optional<String> cluster, Object... values) {
        Map datas = CollectionUtils.mapOf(values);

        datas.put("clusters", this.kafkaModule.getClustersList());
        datas.put("basePath", getBasePath());

        cluster.ifPresent(s -> {
            datas.put("clusterId", s);
            datas.put("registryEnabled", this.kafkaModule.getRegistryRestClient(s) != null);
        });

        return datas;
    }

    @SuppressWarnings("unchecked")
    protected HttpResponse template(HttpRequest request, String cluster, Object... values) {
        Map datas = templateData(Optional.of(cluster), values);

        MutableHttpResponse<Map> response = HttpResponse.ok();

        request.getCookies()
            .findCookie(SESSION_TOAST)
            .ifPresent(s -> {
                datas.put("toast", s.getValue());
                response.cookie(Cookie.of(SESSION_TOAST, "").maxAge(0).path("/"));
            });

        return response.body(datas);
    }

    protected String getBasePath() {
        return basePath.replaceAll("/$","");
    }

    protected URI uri(String path) throws URISyntaxException {
        return new URI((this.basePath != null ? this.basePath : "") + path);
    }

    protected <T> Toast toast(MutableHttpResponse<T> response, Toast toast) {
        Cookie cookie = Cookie
            .of(SESSION_TOAST, gson.toJson(toast
                .withTitle(toast.getTitle() != null ? toast.getTitle().replaceAll(";", ",") : null)
                .withMessage(toast.getMessage() != null ? toast.getMessage().replaceAll(";", ",") : null)
            ))
            .path("/");

        response.cookie(cookie);

        return toast;
    }

    @Builder
    @Getter
    public static class Toast {
        public enum Type {
            success,
            error,
            warning,
            info,
            question
        }

        @Wither
        private String title;

        @Wither
        private String message;

        @Builder.Default
        private Type type = Type.info;
    }
}