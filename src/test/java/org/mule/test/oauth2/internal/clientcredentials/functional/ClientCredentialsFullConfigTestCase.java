/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.oauth2.internal.clientcredentials.functional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.runtime.http.api.HttpHeaders.Names.AUTHORIZATION;
import static org.mule.runtime.http.api.HttpHeaders.Names.WWW_AUTHENTICATE;
import static org.mule.runtime.oauth.api.state.ResourceOwnerOAuthContext.DEFAULT_RESOURCE_OWNER_ID;

import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.SimpleMemoryObjectStore;
import org.mule.runtime.oauth.api.state.ResourceOwnerOAuthContext;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.test.oauth2.AbstractOAuthAuthorizationTestCase;
import org.mule.test.runner.RunnerDelegateTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.Parameterized;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import java.util.Collection;

@RunnerDelegateTo(Parameterized.class)
public class ClientCredentialsFullConfigTestCase extends AbstractOAuthAuthorizationTestCase {

  private final String CUSTOM_RESPONSE_PARAMETER1_VALUE = "token-resp-value1";
  private final String CUSTOM_RESPONSE_PARAMETER2_VALUE = "token-resp-value2";
  private static final String RESOURCE_PATH = "/resource";
  private static final String NEW_ACCESS_TOKEN = "abcdefghjkl";
  @Rule
  public SystemProperty tokenUrl =
      new SystemProperty("token.url", format("%s://localhost:%d" + TOKEN_PATH, getProtocol(), oauthHttpsServerPort.getNumber()));
  @Rule
  public SystemProperty customTokenResponseParameter1Name = new SystemProperty("custom.param.extractor1", "token-resp-param1");
  @Rule
  public SystemProperty customTokenResponseParameter2Name = new SystemProperty("custom.param.extractor2", "token-resp-param2");

  private String[] configFiles;

  @Override
  protected String[] getConfigFiles() {
    return configFiles;
  }

  public ClientCredentialsFullConfigTestCase(String[] configFiles) {
    this.configFiles = configFiles;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    final String operationsConfig = "operations/operations-config.xml";
    return Arrays
        .asList(new Object[][] {
            new String[] {"client-credentials/client-credentials-full-config-tls-global.xml", operationsConfig}},
                new Object[][] {
                    new String[] {"client-credentials/client-credentials-full-config-tls-nested.xml", operationsConfig}});
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    final ImmutableMap customTokenResponseParameters =
        new ImmutableMap.Builder().put(customTokenResponseParameter1Name.getValue(), CUSTOM_RESPONSE_PARAMETER1_VALUE)
            .put(customTokenResponseParameter2Name.getValue(), CUSTOM_RESPONSE_PARAMETER2_VALUE).build();
    configureWireMockToExpectTokenPathRequestForClientCredentialsGrantTypeWithMapResponse(customTokenResponseParameters);
  }

  @Test
  public void authenticationIsDoneOnStartupUsingScope() throws Exception {
    verifyRequestDoneToTokenUrlForClientCredentials(scopes.getValue());

    ObjectStore objectStore = registry.<ObjectStore>lookupByName("customObjectStore").get();
    assertThat(objectStore.allKeys().isEmpty(), is(false));
    ResourceOwnerOAuthContext resourceOwnerOAuthContext =
        (ResourceOwnerOAuthContext) objectStore.retrieve(DEFAULT_RESOURCE_OWNER_ID);
    assertThat(resourceOwnerOAuthContext.getAccessToken(), is(ACCESS_TOKEN));
  }

  @Test
  public void customTokenResponseParametersAreCaptured() throws Exception {
    verifyTokenManagerAccessToken();
    verifyTokenManagerExpiresIn();
    verifyTokenManagerCustomParameterExtractor(customTokenResponseParameter1Name.getValue(), CUSTOM_RESPONSE_PARAMETER1_VALUE);
    verifyTokenManagerCustomParameterExtractor(customTokenResponseParameter2Name.getValue(), CUSTOM_RESPONSE_PARAMETER2_VALUE);
  }

  @Test
  public void authenticationFailedTriggersRefreshAccessToken() throws Exception {
    configureWireMockToExpectTokenPathRequestForClientCredentialsGrantTypeWithMapResponse(NEW_ACCESS_TOKEN);

    wireMockRule.stubFor(post(urlEqualTo(RESOURCE_PATH)).withHeader(AUTHORIZATION, containing(ACCESS_TOKEN))
        .willReturn(aResponse().withStatus(500).withHeader(WWW_AUTHENTICATE, "Basic realm=\"myRealm\"")));

    wireMockRule.stubFor(post(urlEqualTo(RESOURCE_PATH)).withHeader(AUTHORIZATION, containing(NEW_ACCESS_TOKEN))
        .willReturn(aResponse().withBody(TEST_MESSAGE).withStatus(200)));

    flowRunner("testFlow").withPayload(TEST_MESSAGE).run();

    verifyRequestDoneToTokenUrlForClientCredentials();

    wireMockRule
        .verify(postRequestedFor(urlEqualTo(RESOURCE_PATH)).withHeader(AUTHORIZATION, equalTo("Bearer " + NEW_ACCESS_TOKEN)));
  }

  @Override
  protected String getProtocol() {
    return HTTPS.getScheme();
  }
}
