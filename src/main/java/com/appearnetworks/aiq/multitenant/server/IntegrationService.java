package com.appearnetworks.aiq.multitenant.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IntegrationService {
    private static final String ROOT_LINK_CACHE_KEY = "link_";
    private static final String ACCESS_TOKEN_CACHE_KEY = "token";
    private static final String INTEGRATION_LINK_CACHE_KEY = "link_integration_";

    private static final String ADAPTER = "adapter";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    private ObjectMapper mapper = new ObjectMapper();

    @Value("${aiq.url}")
    private String aiqUrl;

    @Value("${aiq.orgname}")
    private String aiqOrgName;

    @Value("${aiq.username}")
    private String aiqUsername;

    @Value("${aiq.password}")
    private String aiqPassword;

    @Value("${aiq.scope:integration}")
    private String aiqScope;

    private void fetchOrgRootMenu() {
        try {
            OrgRootMenu
                    orgRootMenu = new RestTemplate().getForObject(aiqUrl + "?orgName=" + aiqOrgName, OrgRootMenu.class);

            URI baseURL = URI.create(aiqUrl);

            for (Iterator<Map.Entry<String, JsonNode>> iterator = orgRootMenu.getLinks().fields(); iterator.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                cache.put(ROOT_LINK_CACHE_KEY + entry.getKey(), baseURL.resolve(entry.getValue().textValue()));
            }
        } catch (HttpStatusCodeException e) {
            throw reportHttpError(e);
        } catch (ResourceAccessException e) {
            throw new ServerUnavailableException(e.getMessage());
        } catch (HttpMessageConversionException | RestClientException e) {
            throw new ServerException(e.getMessage());
        }
    }

    private String fetchUserToken() {
        if (!cache.containsKey(ACCESS_TOKEN_CACHE_KEY)) {
            fetchAccessToken();
        }
        return (String) cache.get(ACCESS_TOKEN_CACHE_KEY);
    }

    private URI fetchIntegrationLink(String link) {
        if (!cache.containsKey(INTEGRATION_LINK_CACHE_KEY + link)) {
            fetchAccessToken();
        }
        return (URI) cache.get(INTEGRATION_LINK_CACHE_KEY + link);
    }

    private URI fetchRootLink(String link) {
        if (!cache.containsKey(ROOT_LINK_CACHE_KEY + link)) {
            fetchOrgRootMenu();
        }
        return (URI) cache.get(ROOT_LINK_CACHE_KEY + link);
    }

    private void fetchAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "username=" + aiqUsername + "&password=" + aiqPassword + "&grant_type=password&scope=" + aiqScope;
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            URI baseURL = fetchRootLink("token");
            AccessToken accessToken = new RestTemplate().postForObject(baseURL, request, AccessToken.class);

            cache.put(ACCESS_TOKEN_CACHE_KEY, accessToken.getAccess_token());

            for (Iterator<Map.Entry<String, JsonNode>> iterator = accessToken.getLinks().fields(); iterator.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                cache.put(INTEGRATION_LINK_CACHE_KEY + entry.getKey(), baseURL.resolve(entry.getValue().textValue()));
            }
        } catch (HttpStatusCodeException e) {
            throw reportHttpError(e);
        } catch (ResourceAccessException e) {
            throw new ServerUnavailableException(e.getMessage());
        } catch (HttpMessageConversionException | RestClientException e) {
            throw new ServerException(e.getMessage());
        }
    }

    private void invalidateUserToken() {
        cache.remove(ACCESS_TOKEN_CACHE_KEY);
    }

    private String authorizationValue(String token) {
        return "Bearer " + token;
    }

    public void register(String integrationURL, String integrationPassword) {
        ObjectNode request = mapper.valueToTree(new RegisterAdapterRequest(integrationURL, integrationPassword));
        try {
            doPut(fetchIntegrationLink(ADAPTER).toString(), request);
        } catch (UnauthorizedException e) {
            if (fetchUserToken() != null)
                doPut(fetchIntegrationLink(ADAPTER).toString(), request);
            else
                throw e;
        }
    }

    public void unregister() {
        try {
            delete(fetchIntegrationLink(ADAPTER).toString());
        } catch (UnauthorizedException e) {
            if (fetchUserToken() != null)
                delete(fetchIntegrationLink(ADAPTER).toString());
            else
                throw e;
        }
    }

    private boolean doPut(String uri, Object data, Object... parameters) {
        try {
            getRestTemplateWithAuth().put(uri, data, parameters);
            return true;
        } catch (HttpStatusCodeException e) {
            switch (e.getStatusCode()) {
                case NOT_FOUND:
                    return false;

                case UNAUTHORIZED:
                    invalidateUserToken();
                    throw new UnauthorizedException();

                default:
                    throw reportHttpError(e);
            }
        } catch (ResourceAccessException e) {
            throw new ServerUnavailableException(e.getMessage());
        } catch (HttpMessageConversionException | RestClientException e) {
            throw new ServerException(e.getMessage());
        }
    }

    private boolean delete(String url, Object... parameters) {
        try {
            getRestTemplateWithAuth().delete(url, parameters);
            return true;
        } catch (HttpStatusCodeException e) {
            switch (e.getStatusCode()) {
                case NOT_FOUND:
                    return false;

                case UNAUTHORIZED:
                    invalidateUserToken();
                    throw new UnauthorizedException();

                default:
                    throw reportHttpError(e);
            }
        } catch (ResourceAccessException e) {
            throw new ServerUnavailableException(e.getMessage());
        } catch (HttpMessageConversionException | RestClientException e) {
            throw new ServerException(e.getMessage());
        }
    }

    private RestTemplate getRestTemplateWithAuth() {
        ClientHttpRequestInterceptor interceptor = new HeaderHttpRequestInterceptor(fetchUserToken());
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(interceptor);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }

    private RuntimeException reportHttpError(HttpStatusCodeException e) {
        if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE)
            return new ServerUnavailableException();
        else
            return new ServerException(e.getStatusCode(), e.getResponseBodyAsString());
    }

    class HeaderHttpRequestInterceptor implements ClientHttpRequestInterceptor {
        private final String token;

        public HeaderHttpRequestInterceptor(String token) {
            this.token = token;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {

            HttpRequestWrapper requestWrapper = new HttpRequestWrapper(request);
            requestWrapper.getHeaders().add(AUTHORIZATION_HEADER, authorizationValue(token));
            return execution.execute(requestWrapper, body);
        }
    }
}
