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
        // Remove worker node suffixes (Jetty uses . ! or @ followed by worker name)
        for (char c : new char[]{'.', '!', '@'}) {
            int idx = cid.indexOf(c);
            if (idx > 0) cid = cid.substring(0, idx);
        }
        return cid;
    }


    public static String getBridgedUser(HttpServletRequest req, ISecurityManager securityManager)
    {
        try {
            System.err.println("--- [DEBUG] getBridgedUser called for path: " + req.getRequestURI());
            System.err.println("--- [DEBUG] Remote user: " + req.getRemoteUser());
            // 1. First check local session for performance
            javax.servlet.http.HttpSession localSession = req.getSession(false);
            if (localSession != null) {
                System.err.println("--- [DEBUG] localSession id: " + localSession.getId());
                String user = (String) localSession.getAttribute("VERIFIED_USER");
                if (user != null) {
                    System.err.println("--- [DEBUG] found VERIFIED_USER in localSession: " + user);
                    return user;
                }

                // search bridge if VERIFIED_USER missing
                String cid = getCleanId(localSession.getId());
                for (String entry : securityManager.get2FAVerifiedSessions()) {
                    if (entry.startsWith(cid + ":")) {
                        String found = entry.substring(cid.length() + 1);
                        localSession.setAttribute("VERIFIED_USER", found);
                        localSession.setAttribute("2FA_VERIFIED", true);
                        System.err.println("--- [DEBUG] found VERIFIED_USER via bridged localSession: " + found);
                        return found;
                    }
                }
            } else {
                System.err.println("--- [DEBUG] localSession is null");
            }

            // 2. Search all cookies for a bridged session
            String cookieHeader = req.getHeader("Cookie");
            System.err.println("--- [DEBUG] Cookie header: " + cookieHeader);
            if (cookieHeader != null) {
                for (String cookie : cookieHeader.split(";")) {
                    String[] parts = cookie.trim().split("=", 2);
                    if (parts.length == 2 && parts[0].trim().contains("JSESSIONID")) {
                        String cid = getCleanId(parts[1].trim());
                        if (cid != null) {
                            for (String entry : securityManager.get2FAVerifiedSessions()) {
                                if (entry.startsWith(cid + ":")) {
                                    String foundUser = entry.substring(cid.length() + 1);
                                    // Auto-bridge current local session if it exists
                                    if (localSession != null) {
                                        String currentCid = getCleanId(localSession.getId());
                                        securityManager.get2FAVerifiedSessions().add(currentCid + ":" + foundUser);
                                        localSession.setAttribute("2FA_VERIFIED", true);
                                        localSession.setAttribute("VERIFIED_USER", foundUser);
                                    }
                                    System.err.println("--- [DEBUG] found user via cookie JSESSIONID bridge: " + foundUser);
                                    return foundUser;
                                }
                            }
                        }
                    }
                }
            }

            // Check auth header directly to see if it's there
            String authHeader = req.getHeader("Authorization");
            if (authHeader != null) {
                System.err.println("--- [DEBUG] Authorization header is present: " + authHeader.substring(0, Math.min(10, authHeader.length())) + "...");
            }

        } catch (Exception e) {
            System.err.println("--- [DEBUG] Error in getBridgedUser: " + e.getMessage());
        }
        System.err.println("--- [DEBUG] getBridgedUser returning null");
        return null;
    }


    public static void bridgeAllCookies(HttpServletRequest req, String username, ISecurityManager securityManager) {
        try {
            // 1. Bridge current local session
            javax.servlet.http.HttpSession session = req.getSession(false);
            if (session != null) {
                String cid = getCleanId(session.getId());
                if (cid != null) securityManager.get2FAVerifiedSessions().add(cid + ":" + username);
                session.setAttribute("2FA_VERIFIED", true);
                session.setAttribute("VERIFIED_USER", username);
            }

            // 2. Bridge all JSESSIONID-like cookies found in request
            String cookieHeader = req.getHeader("Cookie");
            if (cookieHeader != null) {
                for (String cookie : cookieHeader.split(";")) {
                    String[] parts = cookie.trim().split("=", 2);
                    if (parts.length == 2 && parts[0].trim().contains("JSESSIONID")) {
                        String cid = getCleanId(parts[1].trim());
                        if (cid != null) {
                            String entry = cid + ":" + username;
                            if (!securityManager.get2FAVerifiedSessions().contains(entry)) {
                                securityManager.get2FAVerifiedSessions().add(entry);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
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
        HttpServletRequest req = (HttpServletRequest) request;

        // 1. Check for verified session (Local or Bridged)
        // If they have a valid 2FA session, we can skip repeat password/TOTP checks
        boolean alreadyVerified = false;
        String bridgedUser = getBridgedUser(req, securityManager);

        if (bridgedUser != null && (username == null || username.equals(bridgedUser))) {
            username = bridgedUser;
            alreadyVerified = true;
        }

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

        if (alreadyVerified) {
            return createUserIdentity(user, credentials);
        }
        
        UserIdentity identity = null;
        if (!isCert)
        {
            String storedPwd = user.getPassword();
            if (storedPwd == null) return null;

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

            // Check password
            boolean passwordMatch = false;
            try {
                if (storedPwd.startsWith("PBKDF2WithHmacSHA1:")) {
                    Class<?> providerClass = null;
                    try {
                        providerClass = Class.forName("com.botts.impl.security.PBKDF2CredentialProvider");
                    } catch (ClassNotFoundException e) {
                        try {
                            providerClass = Thread.currentThread().getContextClassLoader().loadClass("com.botts.impl.security.PBKDF2CredentialProvider");
                        } catch (ClassNotFoundException e2) {
                            providerClass = OshLoginService.class.getClassLoader().loadClass("com.botts.impl.security.PBKDF2CredentialProvider");
                        }
                    }
                    java.lang.reflect.Method checkMethod = providerClass.getMethod("check", String.class, String.class);
                    passwordMatch = (Boolean) checkMethod.invoke(null, storedPwd, providedPwd);
                } else {
                    passwordMatch = Credential.getCredential(storedPwd).check(providedPwd) || Credential.getCredential(storedPwd).check(originalPwd);
                }
            } catch (Exception e) {
                passwordMatch = Credential.getCredential(storedPwd).check(providedPwd) || Credential.getCredential(storedPwd).check(originalPwd);
            }

            if (passwordMatch)
            {
                // Check TOTP if enabled
                if (user instanceof org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig)
                {
                    org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig userConfig = (org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig) user;
                    if (userConfig.isTwoFactorEnabled)
                    {
                        boolean verified = false;

                        // Check for new TOTP code in request
                        String code = otp;
                        if (code == null) code = req.getHeader("X-OSH-TOTP");
                        if (code == null) code = req.getParameter("otp");

                        if (code != null && org.sensorhub.impl.security.TOTPUtils.validateCode(userConfig.twoFactorSecret, code))
                        {
                            verified = true;
                            // Bridge all cookies to this user
                            bridgeAllCookies(req, username, securityManager);
                        }
                        else
                        {
                            return null;
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
