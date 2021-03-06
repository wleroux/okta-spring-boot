/*
 * Copyright 2018-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.spring.boot.oauth;

import com.okta.spring.boot.oauth.config.OktaOAuth2Properties;
import com.okta.spring.boot.oauth.http.UserAgentRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoderJwkSupport;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@AutoConfigureBefore(OAuth2ResourceServerAutoConfiguration.class)
@ConditionalOnClass(JwtAuthenticationToken.class)
@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
@EnableConfigurationProperties(OktaOAuth2Properties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class OktaOAuth2ResourceServerAutoConfig {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_REQUEST,
                        "This aud claim is not equal to the configured audience",
                        "https://tools.ietf.org/html/rfc6750#section-3.1");
    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties oAuth2ResourceServerProperties,
                          OktaOAuth2Properties oktaOAuth2Properties) {

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
            validators.add(new JwtTimestampValidator());
            validators.add(new JwtIssuerValidator(oAuth2ResourceServerProperties.getJwt().getIssuerUri()));
            validators.add(token -> {
                Set<String> expectedAudience = new HashSet<>();
                expectedAudience.add(oktaOAuth2Properties.getAudience());
                return !Collections.disjoint(token.getAudience(), expectedAudience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
            });
            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(validators);

            NimbusJwtDecoderJwkSupport decoder = new NimbusJwtDecoderJwkSupport(oAuth2ResourceServerProperties.getJwt().getJwkSetUri());
            decoder.setJwtValidator(validator);
            decoder.setRestOperations(restOperations());

            return decoder;
    }

    private RestOperations restOperations() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new UserAgentRequestInterceptor());
        return restTemplate;
    }
}