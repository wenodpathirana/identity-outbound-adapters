/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.outbound.adapter.websubhub.internal;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.ssl.SSLContexts;
import org.wso2.identity.outbound.adapter.websubhub.exception.WebSubAdapterException;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import static java.util.Objects.isNull;
import static org.wso2.identity.outbound.adapter.websubhub.util.WebSubHubAdapterConstants.ErrorMessages.ERROR_CREATING_ASYNC_HTTP_CLIENT;
import static org.wso2.identity.outbound.adapter.websubhub.util.WebSubHubAdapterConstants.ErrorMessages.ERROR_CREATING_SSL_CONTEXT;
import static org.wso2.identity.outbound.adapter.websubhub.util.WebSubHubAdapterConstants.ErrorMessages.ERROR_GETTING_ASYNC_CLIENT;
import static org.wso2.identity.outbound.adapter.websubhub.util.WebSubHubAdapterUtil.handleServerException;

/**
 * Class to retrieve the HTTP Clients.
 */
public class ClientManager {

    private static final int HTTP_CONNECTION_TIMEOUT = 300;
    private static final int HTTP_READ_TIMEOUT = 300;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 300;

    private final CloseableHttpAsyncClient httpAsyncClient;

    /**
     * Creates a client manager.
     *
     * @throws WebSubAdapterException on errors while creating the http client.
     */
    public ClientManager() throws WebSubAdapterException {

        PoolingNHttpClientConnectionManager connectionManager;
        try {
            connectionManager = createPoolingConnectionManager();
        } catch (IOException e) {
            throw handleServerException(ERROR_CREATING_ASYNC_HTTP_CLIENT, e);
        }

        RequestConfig config = createRequestConfig();
        HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClients.custom().setDefaultRequestConfig(config);
        addSslContext(httpClientBuilder);
        httpClientBuilder.setConnectionManager(connectionManager);
        httpAsyncClient = httpClientBuilder.build();
        httpAsyncClient.start();
    }

    /**
     * Get HTTP client properly configured with tenant configurations.
     *
     * @return CloseableHttpAsyncClient instance.
     */
    public CloseableHttpAsyncClient getClient() throws WebSubAdapterException {

        if (isNull(httpAsyncClient)) {
            throw handleServerException(ERROR_GETTING_ASYNC_CLIENT, null);
        } else if (!httpAsyncClient.isRunning()) {
            httpAsyncClient.start();
        }
        return httpAsyncClient;
    }

    private RequestConfig createRequestConfig() {

        return RequestConfig.custom()
                .setConnectTimeout(HTTP_CONNECTION_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(HTTP_READ_TIMEOUT)
                .setRedirectsEnabled(false)
                .setRelativeRedirectsAllowed(false)
                .build();
    }

    private PoolingNHttpClientConnectionManager createPoolingConnectionManager() throws IOException {

        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
        PoolingNHttpClientConnectionManager poolingHttpClientConnectionMgr = new
                PoolingNHttpClientConnectionManager(ioReactor);
        // Increase max total connection to 20.
        poolingHttpClientConnectionMgr.setMaxTotal(
                WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration().getHttpClientMaxConnections());
        // Increase default max connection per route to 20.
        poolingHttpClientConnectionMgr.setDefaultMaxPerRoute(
                WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration()
                        .getHttpClientMaxConnectionsPerRoute());
        return poolingHttpClientConnectionMgr;
    }

    private void addSslContext(HttpAsyncClientBuilder builder) throws WebSubAdapterException {

        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(WebSubHubAdapterDataHolder.getInstance().getTrustStore(), null)
                    .build();
            builder.setSSLContext(sslContext);
            builder.setSSLHostnameVerifier(new DefaultHostnameVerifier());

        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw handleServerException(ERROR_CREATING_SSL_CONTEXT, e);
        }
    }
}
