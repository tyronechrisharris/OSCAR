#!/bin/bash
patch -p1 << 'PATCH_EOF'
--- a/include/osh-core/sensorhub-core/src/main/java/org/sensorhub/impl/service/BridgedAuthenticator.java
+++ b/include/osh-core/sensorhub-core/src/main/java/org/sensorhub/impl/service/BridgedAuthenticator.java
@@ -60,9 +60,12 @@ public class BridgedAuthenticator implements Authenticator {
         }

         // 2. Check for bridged session
         String username = OshLoginService.getBridgedUser(request, securityManager);
+        System.out.println("DEBUG_WS: BridgedAuthenticator.validateRequest: getBridgedUser returned: " + username + " for URI: " + request.getRequestURI());
         if (username != null) {
             IUserInfo user = securityManager.getUserInfo(username);
             if (user != null) {
+                System.out.println("DEBUG_WS: BridgedAuthenticator.validateRequest: returning UserAuthentication for user: " + username);
                 UserIdentity userIdentity = new OshLoginService(securityManager).createUserIdentity(user, "");
                 return new UserAuthentication(getAuthMethod(), userIdentity);
             }
--- a/include/osh-core/sensorhub-core/src/main/java/org/sensorhub/impl/service/OshLoginService.java
+++ b/include/osh-core/sensorhub-core/src/main/java/org/sensorhub/impl/service/OshLoginService.java
@@ -48,12 +48,15 @@ public class OshLoginService implements LoginService
     public static String getBridgedUser(HttpServletRequest req, ISecurityManager securityManager)
     {
+        System.out.println("DEBUG_WS: OshLoginService.getBridgedUser called for URI: " + req.getRequestURI());
         try {
             // 1. First check local session for performance
             javax.servlet.http.HttpSession localSession = req.getSession(false);
             if (localSession != null) {
+                System.out.println("DEBUG_WS: Found local session: " + localSession.getId());
                 String user = (String) localSession.getAttribute("VERIFIED_USER");
                 if (user != null) return user;

                 // search bridge if VERIFIED_USER missing
                 String cid = getCleanId(localSession.getId());
                 for (String entry : securityManager.get2FAVerifiedSessions()) {
@@ -67,10 +70,12 @@ public class OshLoginService implements LoginService
             }

             // 2. Search all cookies for a bridged session
             String cookieHeader = req.getHeader("Cookie");
+            System.out.println("DEBUG_WS: Cookie header: " + cookieHeader);
             if (cookieHeader != null) {
                 for (String cookie : cookieHeader.split(";")) {
                     String[] parts = cookie.trim().split("=", 2);
                     if (parts.length == 2 && parts[0].trim().contains("JSESSIONID")) {
                         String cid = getCleanId(parts[1].trim());
+                        System.out.println("DEBUG_WS: Checking bridged JSESSIONID: " + cid);
                         if (cid != null) {
                             for (String entry : securityManager.get2FAVerifiedSessions()) {
@@ -84,6 +89,7 @@ public class OshLoginService implements LoginService
                                         localSession.setAttribute("2FA_VERIFIED", true);
                                         localSession.setAttribute("VERIFIED_USER", foundUser);
                                     }
+                                    System.out.println("DEBUG_WS: Found bridged user from JSESSIONID: " + foundUser);
                                     return foundUser;
                                 }
                             }
PATCH_EOF
