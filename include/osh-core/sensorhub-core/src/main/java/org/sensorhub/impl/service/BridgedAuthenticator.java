/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2012-2024 Sensia Software LLC. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service;

import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.UserIdentity;
import org.sensorhub.api.security.ISecurityManager;
import org.sensorhub.api.security.IUserInfo;


public class BridgedAuthenticator implements Authenticator {
    private final Authenticator delegate;
    private final ISecurityManager securityManager;

    public BridgedAuthenticator(Authenticator delegate, ISecurityManager securityManager) {
        this.delegate = delegate;
        this.securityManager = securityManager;
    }

    @Override
    public void setConfiguration(AuthConfiguration configuration) {
        delegate.setConfiguration(configuration);
    }

    @Override
    public String getAuthMethod() {
        return delegate.getAuthMethod();
    }

    @Override
    public void prepareRequest(ServletRequest request) {
        delegate.prepareRequest(request);
    }

    @Override
    public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 1. Trap uninitialized state
        if (securityManager.isUninitialized()) {
            return Authentication.UNAUTHENTICATED;
        }

        // 2. Check for API Key (Machine Auth)
        String apiKeyUser = OshLoginService.getApiKeyUser(request, securityManager);
        if (apiKeyUser != null) {
            IUserInfo user = securityManager.getUserInfo(apiKeyUser);
            if (user != null) {
                UserIdentity userIdentity = new OshLoginService(securityManager).createUserIdentity(user, "API_KEY");
                return new UserAuthentication("API_KEY", userIdentity);
            }
        }

        // 3. Check for bridged session (Human Auth)
        String username = OshLoginService.getBridgedUser(request, securityManager);
        if (username != null) {
            IUserInfo user = securityManager.getUserInfo(username);
            if (user != null) {
                UserIdentity userIdentity = new OshLoginService(securityManager).createUserIdentity(user, "");
                return new UserAuthentication(getAuthMethod(), userIdentity);
            }
        }

        // 4. Bifurcated Logic for Mandatory Auth
        if (mandatory) {
            String uri = request.getRequestURI();
            boolean isHumanRoute = uri.equals("/") || uri.contains("/admin") || uri.contains("/VAADIN") || uri.contains("/setup");

            if (isHumanRoute) {
                // Human routes redirect to login
                try {
                    String contextPath = request.getContextPath();
                    if (contextPath == null || contextPath.isEmpty()) contextPath = "/sensorhub";
                    response.sendRedirect(contextPath + "/login?redirect=" + java.net.URLEncoder.encode(uri, "UTF-8"));
                    return Authentication.SEND_CONTINUE;
                } catch (IOException e) {
                    throw new ServerAuthException(e);
                }
            } else {
                // Machine routes return 401 via delegate (or direct if no delegate)
                return delegate.validateRequest(req, res, mandatory);
            }
        }

        // 5. Fallback for non-mandatory requests
        return delegate.validateRequest(req, res, mandatory);
    }

    @Override
    public boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, Authentication.User validatedUser) throws ServerAuthException {
        return delegate.secureResponse(request, response, mandatory, validatedUser);
    }
}
