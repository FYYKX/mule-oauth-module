/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.oauth2.internal.authorizationcode.functional;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mule.runtime.oauth.api.state.ResourceOwnerOAuthContext.DEFAULT_RESOURCE_OWNER_ID;
import org.mule.extension.http.api.request.validator.ResponseValidatorException;
import org.mule.functional.junit4.rules.ExpectedError;

import org.junit.Rule;
import org.junit.Test;

public class AuthorizationCodeRefreshTokenConfigTestCase extends AbstractAuthorizationCodeRefreshTokenConfigTestCase {

  @Rule
  public ExpectedError expectedError = ExpectedError.none();

  public static final String SINGLE_TENANT_OAUTH_CONFIG = "oauthConfig";

  @Override
  protected String getConfigFile() {
    return "authorization-code/authorization-code-refresh-token-config.xml";
  }

  @Test
  public void afterFailureDoRefreshTokenWithDefaultValueNoResourceOwnerId() throws Exception {
    executeRefreshToken("testFlow", SINGLE_TENANT_OAUTH_CONFIG, DEFAULT_RESOURCE_OWNER_ID, 403);
  }

  /**
   * Refresh token is optional therefore this test will validate an scenario where the access_token is invalid and refresh_token
   * provided in previous token access has been revoked so a {@link ResponseValidatorException} should be thrown.
   *
   * @throws Exception
   */
  @Test
  public void afterFailureWithRefreshTokenNotIssuedThrowAuthenticationException() throws Exception {
    expectedError.expectCause(is(instanceOf(ResponseValidatorException.class)));
    executeRefreshTokenUsingOldRefreshTokenOnTokenCallAndRevokedByUsers("testFlow", SINGLE_TENANT_OAUTH_CONFIG,
                                                                        DEFAULT_RESOURCE_OWNER_ID, 403, 400);
  }
}
