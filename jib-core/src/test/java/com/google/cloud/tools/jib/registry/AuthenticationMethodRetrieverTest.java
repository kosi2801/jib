/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.registry;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.common.collect.Lists;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link AuthenticationMethodRetriever}. */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationMethodRetrieverTest {

  @Mock private ResponseException mockResponseException;
  @Mock private HttpHeaders mockHeaders;
  @Mock private FailoverHttpClient httpClient;

  private final RegistryEndpointRequestProperties fakeRegistryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someServerUrl", "someImageName");
  private final AuthenticationMethodRetriever testAuthenticationMethodRetriever =
      new AuthenticationMethodRetriever(
          fakeRegistryEndpointRequestProperties, "user-agent", httpClient);

  @Test
  public void testGetContent() {
    Assert.assertNull(testAuthenticationMethodRetriever.getContent());
  }

  @Test
  public void testGetAccept() {
    Assert.assertEquals(0, testAuthenticationMethodRetriever.getAccept().size());
  }

  @Test
  public void testHandleResponse() {
    Assert.assertFalse(
        testAuthenticationMethodRetriever.handleResponse(Mockito.mock(Response.class)).isPresent());
  }

  @Test
  public void testGetApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/"),
        testAuthenticationMethodRetriever.getApiRoute("http://someApiBase/"));
  }

  @Test
  public void testGetHttpMethod() {
    Assert.assertEquals(HttpMethods.GET, testAuthenticationMethodRetriever.getHttpMethod());
  }

  @Test
  public void testGetActionDescription() {
    Assert.assertEquals(
        "retrieve authentication method for someServerUrl",
        testAuthenticationMethodRetriever.getActionDescription());
  }

  @Test
  public void testHandleHttpResponseException_invalidStatusCode() throws RegistryErrorException {
    Mockito.when(mockResponseException.getStatusCode()).thenReturn(-1);

    try {
      testAuthenticationMethodRetriever.handleHttpResponseException(mockResponseException);
      Assert.fail(
          "Authentication method retriever should only handle HTTP 401 Unauthorized errors");

    } catch (ResponseException ex) {
      Assert.assertEquals(mockResponseException, ex);
    }
  }

  @Test
  public void testHandleHttpResponseException_noHeader() throws ResponseException {
    Mockito.when(mockResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockResponseException.getHeaders()).thenReturn(mockHeaders);
    Mockito.when(mockHeaders.getAuthenticateAsList()).thenReturn(null);

    try {
      testAuthenticationMethodRetriever.handleHttpResponseException(mockResponseException);
      Assert.fail(
          "Authentication method retriever should fail if 'WWW-Authenticate' header is not found");

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("'WWW-Authenticate' header not found"));
    }
  }

  @Test
  public void testHandleHttpResponseException_badAuthenticationMethod() throws ResponseException {
    String authenticationMethod = "bad authentication method";

    Mockito.when(mockResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockResponseException.getHeaders()).thenReturn(mockHeaders);
    Mockito.when(mockHeaders.getAuthenticateAsList())
        .thenReturn(Lists.newArrayList(authenticationMethod));

    try {
      testAuthenticationMethodRetriever.handleHttpResponseException(mockResponseException);
      Assert.fail(
          "Authentication method retriever should fail if 'WWW-Authenticate' header failed to parse");

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Failed getting supported authentication method from 'WWW-Authenticate' header"));
    }
  }

  @Test
  public void testHandleHttpResponseException_pass()
      throws RegistryErrorException, ResponseException, MalformedURLException {
    String authenticationMethod =
        "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"";

    Mockito.when(mockResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockResponseException.getHeaders()).thenReturn(mockHeaders);
    Mockito.when(mockHeaders.getAuthenticateAsList())
        .thenReturn(Lists.newArrayList(authenticationMethod));

    RegistryAuthenticator registryAuthenticator =
        testAuthenticationMethodRetriever.handleHttpResponseException(mockResponseException).get();

    Assert.assertEquals(
        new URL("https://somerealm?service=someservice&scope=repository:someImageName:someScope"),
        registryAuthenticator.getAuthenticationUrl(
            null, Collections.singletonMap("someImageName", "someScope")));
  }

  @Test
  public void testHandleHttpResponseExceptionWithKerberosFirst_pass()
      throws RegistryErrorException, ResponseException, MalformedURLException {
    String authenticationMethodNegotiate = "Negotiate";
    String authenticationMethodBearer =
        "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"";

    Mockito.when(mockResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockResponseException.getHeaders()).thenReturn(mockHeaders);
    Mockito.when(mockHeaders.getAuthenticateAsList())
        .thenReturn(Lists.newArrayList(authenticationMethodNegotiate, authenticationMethodBearer));

    RegistryAuthenticator registryAuthenticator =
        testAuthenticationMethodRetriever.handleHttpResponseException(mockResponseException).get();

    Assert.assertEquals(
        new URL("https://somerealm?service=someservice&scope=repository:someImageName:someScope"),
        registryAuthenticator.getAuthenticationUrl(
            null, Collections.singletonMap("someImageName", "someScope")));
  }
}
