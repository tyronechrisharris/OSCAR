/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service;

import java.security.Principal;
import javax.security.auth.Subject;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.security.Credential;
import org.sensorhub.api.security.ISecurityManager;
import org.sensorhub.api.security.IUserInfo;


public class OshLoginService implements LoginService
{
    final ISecurityManager securityManager;
    IdentityService identityService = new DefaultIdentityService();

    public static String getCleanId(String id)
    {
        if (id == null) return null;
        String cid = id.trim();
        if (cid.startsWith("\"") && cid.endsWith("\"")) cid = cid.substring(1, cid.length()-1);
        int dot = cid.indexOf('.');
        if (dot > 0) cid = cid.substring(0, dot);
        return cid;
    }

    
    public static class UserPrincipal implements Principal
    {
        private final IUserInfo user;
        
        public UserPrincipal(IUserInfo user)
        {
            this.user = user;
        }
        
        @Override
        public String getName()
        {
            return user.getId();
        }
        
        @Override
        public String toString()
        {
            return getName();
        }
    }
    
    
    public static class RolePrincipal implements Principal
    {
        private final String _roleName;
        
        public RolePrincipal(String name)
        {
            _roleName=name;
        }
        
        @Override
        public String getName()
        {
            return _roleName;
        }
    }
    
    
    public OshLoginService(ISecurityManager securityManager)
    {
        this.securityManager = securityManager;
    }
    
    
    @Override
    public IdentityService getIdentityService()
    {
        return identityService;
    }


    @Override
    public String getName()
    {
        return "OpenSensorHub: Authentication Required";
    }


    @Override
    public UserIdentity login(String username, Object credentials, ServletRequest request)
    {
        if (username == null)
            return null;
        
        boolean isCert = false;
        if (username.startsWith("CN="))
        {
            username = username.substring(3, username.indexOf(','));
            isCert = true;
        }
        
        IUserInfo user = securityManager.getUserInfo(username);
        if (user == null)
            return null;
        
        UserIdentity identity = null;
        if (!isCert)
        {
            String storedPwd = user.getPassword();
            if (storedPwd == null) return null;

            Credential storedCredential;
            if (storedPwd.startsWith("PBKDF2WithHmacSHA1:")) {
                try {
                    java.lang.reflect.Method fromEncoded = Class.forName("com.botts.impl.security.PBKDF2Credential").getMethod("fromEncoded", String.class);
                    storedCredential = (Credential) fromEncoded.invoke(null, storedPwd);
                } catch (Exception e) {
                    storedCredential = Credential.getCredential(storedPwd);
                }
            } else {
                storedCredential = Credential.getCredential(storedPwd);
            }

            String originalPwd;
            if (credentials instanceof char[]) originalPwd = new String((char[])credentials);
            else if (credentials instanceof org.eclipse.jetty.util.security.Password) originalPwd = credentials.toString();
            else originalPwd = String.valueOf(credentials);

            String providedPwd = originalPwd;
            String otp = null;

            // Try to extract OTP from password (format password:otp)
            if (providedPwd.length() > 6 && providedPwd.contains(":"))
            {
                int idx = providedPwd.lastIndexOf(':');
                String possibleOtp = providedPwd.substring(idx + 1);
                if (possibleOtp.length() == 6 && possibleOtp.chars().allMatch(Character::isDigit))
                {
                    otp = possibleOtp;
                    providedPwd = providedPwd.substring(0, idx);
                }
            }

            if (storedCredential.check(providedPwd) || storedCredential.check(originalPwd) || storedCredential.check(credentials))
            {
                // Check TOTP if enabled
                if (user instanceof org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig)
                {
                    org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig userConfig = (org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig) user;
                    if (userConfig.isTwoFactorEnabled)
                    {
                        // Check if already verified in session
                        HttpServletRequest req = (HttpServletRequest) request;
                        javax.servlet.http.HttpSession session = null;
                        try {
                            session = req.getSession(false);
                        } catch (IllegalStateException e) {
                            // No session manager
                        }

                        boolean verified = false;

                        // Check context-local session
                        if (session != null) {
                            Boolean b = (Boolean) session.getAttribute("2FA_VERIFIED");
                            if (b != null && b) {
                                verified = true;
                                // Ensure bridged
                                String currentCid = getCleanId(session.getId());
                                if (currentCid != null) securityManager.get2FAVerifiedSessions().add(currentCid + ":" + username);
                            }
                        }

                        // Check bridge via cookies if not verified locally
                        if (!verified) {
                            try {
                                javax.servlet.http.Cookie[] cookies = ((HttpServletRequest)request).getCookies();
                                if (cookies != null) {
                                    for (javax.servlet.http.Cookie c : cookies) {
                                        // Check any OSH session or common session cookie
                                        if (c.getName().startsWith("OSH_JSESSIONID") || c.getName().equals("JSESSIONID")) {
                                            String cid = getCleanId(c.getValue());
                                            if (cid != null && securityManager.get2FAVerifiedSessions().contains(cid + ":" + username)) {
                                                verified = true;
                                                if (session == null) {
                                                    try { session = ((HttpServletRequest)request).getSession(true); } catch (Exception e) {}
                                                }
                                                if (session != null) session.setAttribute("2FA_VERIFIED", true);

                                                // Also add current session to bridge
                                                if (session != null) {
                                                    String currentCid = getCleanId(session.getId());
                                                    if (currentCid != null) securityManager.get2FAVerifiedSessions().add(currentCid + ":" + username);
                                                }
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }

                        // If still not verified, check for new TOTP code in request
                        if (!verified)
                        {
                            String code = otp;
                            if (code == null) code = req.getHeader("X-OSH-TOTP");
                            if (code == null) code = req.getParameter("otp");

                            if (code != null && org.sensorhub.impl.security.TOTPUtils.validateCode(userConfig.twoFactorSecret, code))
                            {
                                verified = true;
                                if (session == null) {
                                    try { session = ((HttpServletRequest)request).getSession(true); } catch (Exception e) {}
                                }
                                if (session != null) session.setAttribute("2FA_VERIFIED", true);

                                // Bridge all OSH-related session cookies
                                try {
                                    javax.servlet.http.Cookie[] cookies = ((HttpServletRequest)request).getCookies();
                                    if (cookies != null) {
                                        for (javax.servlet.http.Cookie c : cookies) {
                                            if (c.getName().startsWith("OSH_JSESSIONID") || c.getName().equals("JSESSIONID")) {
                                                String cid = getCleanId(c.getValue());
                                                if (cid != null) securityManager.get2FAVerifiedSessions().add(cid + ":" + username);
                                            }
                                        }
                                    }
                                } catch (Exception e) {}
                                if (session != null) {
                                    String cid = getCleanId(session.getId());
                                    if (cid != null) securityManager.get2FAVerifiedSessions().add(cid + ":" + username);
                                }
                            }
                            else
                            {
                                return null;
                            }
                        }
                    }
                }
                identity = createUserIdentity(user, credentials);
            }
        }
        else
            identity = createUserIdentity(user, credentials);
        
        return identity;
    }
    
    
    protected UserIdentity createUserIdentity(final IUserInfo user, Object credential)
    {
        Principal principal = new UserPrincipal(user);
        Subject subject = new Subject();
        subject.getPrincipals().add(principal);
        subject.getPrivateCredentials().add(credential);
        subject.setReadOnly();
        
        String[] roles = user.getRoles().toArray(new String[0]);
        UserIdentity identity = identityService.newUserIdentity(subject, principal, roles);
        return identity;
    }


    @Override
    public void logout(UserIdentity user)
    {
    }


    @Override
    public void setIdentityService(IdentityService identityService)
    {
        this.identityService = identityService;
    }


    @Override
    public boolean validate(UserIdentity user)
    {
        return true;
    }

}
