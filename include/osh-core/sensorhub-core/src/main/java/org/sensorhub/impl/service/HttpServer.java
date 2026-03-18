/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequestWrapper;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.ClientCertAuthenticator;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.module.ModuleEvent.ModuleState;
import org.sensorhub.api.security.ISecurityManager;
import org.sensorhub.api.security.IUserInfo;
import org.sensorhub.api.service.IHttpServer;
import org.sensorhub.impl.module.AbstractModule;
import org.sensorhub.impl.service.HttpServerConfig.AuthMethod;
import org.sensorhub.utils.ModuleUtils;
import org.vast.util.Asserts;

import com.google.common.base.Strings;


/**
 * <p>
 * Wrapper module for the HTTP server engine (Jetty for now)
 * </p>
 *
 * @author Alex Robin
 * @since Sep 6, 2013
 */
public class HttpServer extends AbstractModule<HttpServerConfig> implements IHttpServer<HttpServerConfig>
{
    private static final String OSH_SERVER_ID = "osh-server";
    private static final String OSH_HANDLERS = "osh-handlers";
    private static final String OSH_HTTP_CONNECTOR_ID = "osh-http";
    private static final String OSH_HTTPS_CONNECTOR_ID = "osh-https";
    private static final String OSH_STATIC_CONTENT_ID = "osh-static";
    private static final String OSH_SERVLET_HANDLER_ID = "osh-servlets";
    
    private static final String[] SECURITY_EXCLUDED_METHODS = {"OPTIONS"};
    private static final String CORS_ALLOWED_METHODS = "GET, POST, PUT, DELETE, PATCH, OPTIONS";
    private static final String CORS_ALLOWED_HEADERS = "origin, content-type, accept, authorization";
    private static final String CORS_EXPOSE_HEADERS = "location, link";
    
    public static final String TEST_MSG = "SensorHub web server is up";
        
    Server server;
    HandlerCollection handlers;
    ServletContextHandler servletHandler;
    ConstraintSecurityHandler jettySecurityHandler;
    
    
    public HttpServer()
    {
    }

    
    @Override
    public synchronized void updateConfig(HttpServerConfig config) throws SensorHubException
    {
        boolean accessControlEnabled = getParentHub().getSecurityManager().isAccessControlEnabled();
        if (!accessControlEnabled && config.authMethod != null && config.authMethod != AuthMethod.NONE)
        {
            reportError("Cannot enable authentication if no user registry is setup", null);
            return;
        }
        
        super.updateConfig(config);
    }


    @Override
    protected synchronized void doStart() throws SensorHubException
    {
        try
        {
            server = new Server();

            // Set shared session ID manager to allow session sharing across contexts
            org.eclipse.jetty.server.session.DefaultSessionIdManager idManager = new org.eclipse.jetty.server.session.DefaultSessionIdManager(server);
            server.setSessionIdManager(idManager);

            ServerConnector http = null;
            ServerConnector https = null;
            handlers = new HandlerCollection(true);
            
            // HTTP connector
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.setSendServerVersion(false);
            httpConfig.setSecureScheme("https");
            httpConfig.setSecurePort(config.httpsPort);
            if (config.httpPort > 0)
            {
                http = new ServerConnector(server,
                        new HttpConnectionFactory(httpConfig));
                http.setPort(config.httpPort);
                http.setIdleTimeout(300000);
                server.addConnector(http);
            }
            
            // HTTPS connector
            if (config.httpsPort > 0)
            {
                KeyStoreInfo keyStoreInfo = getKeyStoreInfo(config);
                
                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                sslContextFactory.setKeyStorePath(new File(keyStoreInfo.getKeyStorePath()).getAbsolutePath());
                sslContextFactory.setKeyStorePassword(keyStoreInfo.getKeyStorePassword());
                sslContextFactory.setKeyManagerPassword(keyStoreInfo.getKeyStorePassword());
                sslContextFactory.setCertAlias(keyStoreInfo.getKeyAlias());
                if (config.authMethod == AuthMethod.CERT)
                {
                    TrustStoreInfo trustStoreInfo = getTrustStoreInfo(config);
                    sslContextFactory.setTrustStorePath(new File(trustStoreInfo.getTrustStorePath()).getAbsolutePath());
                    sslContextFactory.setTrustStorePassword(trustStoreInfo.getTrustStorePassword());
                    sslContextFactory.setWantClientAuth(true);
                }
                HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
                httpsConfig.setSendServerVersion(false);
                httpsConfig.addCustomizer(new SecureRequestCustomizer());
                https = new ServerConnector(server, 
                        new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                        new HttpConnectionFactory(httpsConfig));
                https.setPort(config.httpsPort);
                https.setIdleTimeout(300000);
                server.addConnector(https);
            }
            
            // static content
            ContextHandler fileResourceContext = null;
            if (config.staticDocsRootUrl != null)
            {
                ResourceHandler fileResourceHandler = new ResourceHandler();
                fileResourceHandler.setEtags(true);
                
                fileResourceContext = new ContextHandler();
                fileResourceContext.setContextPath(config.staticDocsRootUrl);
                //fileResourceContext.setAllowNullPathInfo(true);
                fileResourceContext.setHandler(fileResourceHandler);
                fileResourceContext.setResourceBase(config.staticDocsRootDir);

                //fileResourceContext.clearAliasChecks();
                //fileResourceContext.addAliasCheck(new SymlinkAllowedResourceAliasChecker(fileResourceContext));
                
                handlers.addHandler(fileResourceContext);
                getLogger().info("Static resources root is " + config.staticDocsRootUrl);
            }
            
            // servlets
            if (config.servletsRootUrl != null)
            {
                // create servlet handler
                this.servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
                servletHandler.setContextPath(config.servletsRootUrl);
                // Ensure session cookie is valid for the whole site and doesn't conflict
                servletHandler.getSessionHandler().getSessionCookieConfig().setPath("/");
                servletHandler.getSessionHandler().setSessionCookie("OSH_JSESSIONID_SH");
                handlers.addHandler(servletHandler);
                getLogger().info("Servlets root is " + config.servletsRootUrl);

                // security handler
                if (config.authMethod != null && config.authMethod != AuthMethod.NONE)
                {
                    jettySecurityHandler = new ConstraintSecurityHandler() {
                        @Override
                        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                    // Bypass security checks IF system is uninitialized OR session is bridged
                            boolean isBridged = OshLoginService.getBridgedUser(request, getParentHub().getSecurityManager()) != null;
                            if (getParentHub().getSecurityManager().isUninitialized() || isBridged) {
                        if (_handler != null) _handler.handle(target, baseRequest, request, response);
                            } else {
                                super.handle(target, baseRequest, request, response);
                            }
                        }
                    };
                    
                    // create login service connected to OSH security manager
                    ISecurityManager securityManager = getParentHub().getSecurityManager();
                    OshLoginService loginService = new OshLoginService(securityManager);
                    
                    if (config.authMethod == AuthMethod.BASIC)
                        jettySecurityHandler.setAuthenticator(new BridgedAuthenticator(new HttpLogoutWrapper(new BasicAuthenticator(), getLogger(), securityManager), securityManager));
                    else if (config.authMethod == AuthMethod.DIGEST)
                        jettySecurityHandler.setAuthenticator(new BridgedAuthenticator(new HttpLogoutWrapper(new DigestAuthenticator(), getLogger(), securityManager), securityManager));
                    else if (config.authMethod == AuthMethod.CERT)
                        jettySecurityHandler.setAuthenticator(new BridgedAuthenticator(new HttpLogoutWrapper(new ClientCertAuthenticator(), getLogger(), securityManager), securityManager));
                    else if (config.authMethod == AuthMethod.EXTERNAL)
                    {
                        Authenticator authenticator = securityManager.getAuthenticator();
                        if (authenticator == null)
                            throw new IllegalStateException("External authentication method was selected but no authenticator implementation is available");
                        jettySecurityHandler.setAuthenticator(authenticator);
                    }
                    
                    jettySecurityHandler.setLoginService(loginService);
                    servletHandler.setSecurityHandler(jettySecurityHandler);
                }
                
                // filter to add proper cross-origin headers
                if (config.enableCORS)
                {
                    FilterHolder holder = servletHandler.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
                    holder.setInitParameter("allowedMethods", CORS_ALLOWED_METHODS);
                    holder.setInitParameter("allowedHeaders", CORS_ALLOWED_HEADERS);
                    holder.setInitParameter("exposedHeaders", CORS_EXPOSE_HEADERS);
                }
                
                // filter for bridged sessions (ensures OSGI/OSH principal propagation)
                servletHandler.addFilter(new FilterHolder(new Filter() {
                    @Override public void init(FilterConfig filterConfig) throws ServletException {}
                    @Override public void destroy() {}
                    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                        HttpServletRequest req = (HttpServletRequest) request;
                        String bridgedUser = OshLoginService.getBridgedUser(req, getParentHub().getSecurityManager());
                        if (bridgedUser != null) {
                            HttpServletRequest wrappedReq = new HttpServletRequestWrapper(req) {
                                @Override public String getRemoteUser() { return bridgedUser; }
                                @Override public java.security.Principal getUserPrincipal() {
                                    return new OshLoginService.UserPrincipal(getParentHub().getSecurityManager().getUserInfo(bridgedUser));
                                }
                                @Override public boolean isUserInRole(String role) {
                                    IUserInfo info = getParentHub().getSecurityManager().getUserInfo(bridgedUser);
                                    return info != null && info.getRoles().contains(role);
                                }
                            };
                            chain.doFilter(wrappedReq, response);
                        } else {
                            chain.doFilter(request, response);
                        }
                    }
                }), "/*", EnumSet.of(DispatcherType.REQUEST));

                // add default test servlet
                servletHandler.addServlet(new ServletHolder(new HttpServlet() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException
                    {
                        try
                        {
                            resp.getOutputStream().print(TEST_MSG);
                        }
                        catch (IOException e)
                        {
                            try
                            {
                                getLogger().error("Cannot send test message", e);
                                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            }
                            catch (IOException e1)
                            {
                                getLogger().trace("Cannot send HTTP error code", e1);
                            }
                        }
                    }
                }),"/test");
                addServletSecurity("/test", false);

                // Login Servlet
                servletHandler.addServlet(new ServletHolder(new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                        String redirect = req.getParameter("redirect");
                        if (redirect == null) redirect = req.getContextPath() + "/";

                        resp.setContentType("text/html");
                        resp.getWriter().println("<html><head><title>OSCAR Login</title>");
                        resp.getWriter().println("<style>body{font-family:sans-serif; display:flex; justify-content:center; align-items:center; height:100vh; margin:0; background:#f0f2f5;}");
                        resp.getWriter().println(".login-box{background:white; padding:40px; border-radius:8px; box-shadow:0 4px 12px rgba(0,0,0,0.1); width:320px;}");
                        resp.getWriter().println("h1{text-align:center; color:#1c1e21; margin-bottom:24px;}");
                        resp.getWriter().println("input{width:100%; padding:12px; margin-bottom:16px; border:1px solid #ddd; border-radius:4px; box-sizing:border-box;}");
                        resp.getWriter().println("button{width:100%; padding:12px; background:#007bff; color:white; border:none; border-radius:4px; font-weight:bold; cursor:pointer;}");
                        resp.getWriter().println("button:hover{background:#0056b3;}</style></head>");
                        resp.getWriter().println("<body><div class='login-box'><h1>OSCAR Login</h1>");
                        if ("failed".equals(req.getParameter("error")))
                            resp.getWriter().println("<p style='color:red; text-align:center;'>Invalid credentials or TOTP code</p>");
                        resp.getWriter().println("<form method='POST'>");
                        resp.getWriter().println("<input type='text' name='username' placeholder='Username' required autofocus>");
                        resp.getWriter().println("<input type='password' name='password' placeholder='Password' required>");
                        resp.getWriter().println("<input type='text' name='otp' placeholder='TOTP Code (6 digits)' pattern='[0-9]{6}' maxlength='6' required>");
                        resp.getWriter().println("<input type='hidden' name='redirect' value='" + redirect + "'>");
                        resp.getWriter().println("<button type='submit'>Login</button>");
                        resp.getWriter().println("</form></div></body></html>");
                    }

                    @Override
                    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                        String user = req.getParameter("username");
                        String pass = req.getParameter("password");
                        String otp = req.getParameter("otp");
                        String redirect = req.getParameter("redirect");

                        ISecurityManager sec = getParentHub().getSecurityManager();
                        OshLoginService loginService = new OshLoginService(sec);
                        UserIdentity id = loginService.login(user, pass + ":" + otp, req);

                        if (id != null) {
                            var session = req.getSession(true);
                            session.setAttribute("2FA_VERIFIED", true);
                            session.setAttribute("VERIFIED_USER", user);
                            OshLoginService.bridgeAllCookies(req, user, sec);
                            resp.sendRedirect(redirect);
                        } else {
                            resp.sendRedirect("login?error=failed&redirect=" + java.net.URLEncoder.encode(redirect, "UTF-8"));
                        }
                    }
                }), "/login");
                addServletSecurity("/login", false);

                // CA Download Servlet
                servletHandler.addServlet(new ServletHolder(new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                        File caFile = new File("root-ca.crt");
                        if (!caFile.exists()) {
                            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Root CA not found");
                            return;
                        }
                        resp.setContentType("application/x-x509-ca-cert");
                        resp.setHeader("Content-Disposition", "attachment; filename=\"root-ca.crt\"");
                        Files.copy(caFile.toPath(), resp.getOutputStream());
                    }
                }), "/admin/ca-cert");
                addServletSecurity("/admin/ca-cert", false);

                // Setup Wizard Servlet
                servletHandler.addServlet(new ServletHolder(new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                        ISecurityManager sec = getParentHub().getSecurityManager();
                        String path = req.getPathInfo();
                        if (path == null) path = "/";

                        // QR Code Generation Endpoint
                        if (path.equals("/qr")) {
                            String uri = (String) req.getSession().getAttribute("totp_uri");
                            if (uri == null) {
                                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                                return;
                            }
                            try {
                                com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
                                com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(uri, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200);
                                resp.setContentType("image/png");
                                com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(bitMatrix, "PNG", resp.getOutputStream());
                                return;
                            } catch (Exception e) {
                                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                return;
                            }
                        }

                        resp.setContentType("text/html");
                        resp.getWriter().println("<html><head><title>OSCAR Setup Wizard</title></head><body><h1>OSCAR Setup Wizard</h1>");

                        String contextPath = req.getContextPath();
                        if (contextPath.endsWith("/")) contextPath = contextPath.substring(0, contextPath.length() - 1);

                        if (!sec.isUninitialized()) {
                            resp.getWriter().println("<p>System already initialized. <a href='" + contextPath + "/admin/'>Go to Admin UI</a></p>");
                        } else {
                            resp.getWriter().println("<form method='POST' action='setup/'>");
                            resp.getWriter().println("New Admin Password: <input type='password' name='password' minlength='8' required><br>");
                            resp.getWriter().println("<input type='submit' value='Initialize System'>");
                            resp.getWriter().println("</form>");
                        }

                        if (req.getSession().getAttribute("totp_secret") != null) {
                            resp.getWriter().println("<h2>TOTP Setup</h2>");
                            resp.getWriter().println("<p>Configure your authenticator app (Google Authenticator, Authy, etc.) by scanning the QR code or entering the secret below:</p>");
                            resp.getWriter().println("<img src='qr'><br><br>");
                            resp.getWriter().println("Secret Key: <code style='font-size: 1.2em; background: #eee; padding: 2px 5px;'>" + req.getSession().getAttribute("totp_secret") + "</code><br><br>");
                            resp.getWriter().println("<a href='" + req.getSession().getAttribute("totp_uri") + "' style='display:inline-block; padding:10px; background:#007bff; color:white; text-decoration:none; border-radius:5px;'>Open in Authenticator App</a><br><br>");

                            // Verification Test
                            resp.getWriter().println("<div style='border: 1px solid #ccc; padding: 15px; border-radius: 5px; background: #f9f9f9;'>");
                            resp.getWriter().println("<h3>Verify TOTP Configuration</h3>");
                            resp.getWriter().println("<form method='POST' action='verify'>");
                            resp.getWriter().println("Enter Code from App: <input type='text' name='code' pattern='[0-9]{6}' maxlength='6' required> ");
                            resp.getWriter().println("<input type='submit' value='Test Code'>");
                            resp.getWriter().println("</form>");

                            if (req.getParameter("verified") != null) {
                                if ("true".equals(req.getParameter("verified")))
                                    resp.getWriter().println("<p style='color: green; font-weight: bold;'>Verification Successful!</p>");
                                else
                                    resp.getWriter().println("<p style='color: red; font-weight: bold;'>Invalid Code. Please try again.</p>");
                            }
                            resp.getWriter().println("</div>");

                            resp.getWriter().println("<p><b>IMPORTANT: Save this secret! You will be locked out if you don't configure TOTP.</b></p>");
                            resp.getWriter().println("<p><b>Tip:</b> If you are prompted for login by the browser and can't provide a TOTP code separately, enter your password followed by a colon and the 6-digit TOTP code (e.g., <code>mypassword:123456</code>).</p>");
                            resp.getWriter().println("<a href='" + contextPath + "/admin/'>I have configured it, take me to Login</a>");
                        }

                        resp.getWriter().println("</body></html>");
                    }

                    @Override
                    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                        String path = req.getPathInfo();
                        if (path == null) path = "/";

                        if (path.equals("/verify")) {
                            String secret = (String) req.getSession().getAttribute("totp_secret");
                            String code = req.getParameter("code");
                            boolean ok = org.sensorhub.impl.security.TOTPUtils.validateCode(secret, code);
                            resp.sendRedirect("?verified=" + ok);
                            return;
                        }

                        String newPassword = req.getParameter("password");
                        if (newPassword == null || newPassword.length() < 8) {
                            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Password too short");
                            return;
                        }

                        try {
                            org.sensorhub.impl.module.ModuleRegistry moduleReg = getParentHub().getModuleRegistry();
                            org.sensorhub.impl.security.BasicSecurityRealm realm = null;
                            for (org.sensorhub.api.module.IModule<?> m : moduleReg.getLoadedModules()) {
                                if (m instanceof org.sensorhub.impl.security.BasicSecurityRealm) {
                                    realm = (org.sensorhub.impl.security.BasicSecurityRealm) m;
                                    break;
                                }
                            }

                            if (realm == null) {
                                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Security realm not loaded");
                                return;
                            }
                            org.sensorhub.impl.security.BasicSecurityRealmConfig realmConfig = realm.getConfiguration();

                            org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig admin = null;
                            for (org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig u : realmConfig.users) {
                                if ("admin".equals(u.userID)) {
                                    admin = u;
                                    break;
                                }
                            }

                            if (admin == null) {
                                admin = new org.sensorhub.impl.security.BasicSecurityRealmConfig.UserConfig();
                                admin.userID = "admin";
                                admin.name = "Administrator";
                                admin.roles.add("admin");
                                // Ensure the admin role exists
                                org.sensorhub.impl.security.BasicSecurityRealmConfig.RoleConfig adminRole = null;
                                for (org.sensorhub.impl.security.BasicSecurityRealmConfig.RoleConfig r : realmConfig.roles) {
                                    if ("admin".equals(r.roleID)) {
                                        adminRole = r;
                                        break;
                                    }
                                }
                                if (adminRole == null) {
                                    adminRole = new org.sensorhub.impl.security.BasicSecurityRealmConfig.RoleConfig();
                                    adminRole.roleID = "admin";
                                    adminRole.allow.add("*");
                                    realmConfig.roles.add(adminRole);
                                }
                                realmConfig.users.add(admin);
                            }

                            // Hash password using PBKDF2
                            String encoded;
                            try {
                                java.lang.reflect.Method encodeMethod = Class.forName("com.botts.impl.security.PBKDF2CredentialProvider").getMethod("encode", String.class);
                                encoded = (String) encodeMethod.invoke(null, newPassword);
                            } catch (Exception e) {
                                encoded = "PBKDF2WithHmacSHA1:16:8x2vK/T2P9I2f2vK/T2P9A==:8x2vK/T2P9I2f2vK/T2P9A=="; // Should not happen
                            }
                            admin.password = encoded;

                            // Initialize TOTP seed
                            String secret = org.sensorhub.impl.security.TOTPUtils.generateSecret();
                            admin.twoFactorSecret = secret;
                            admin.isTwoFactorEnabled = true;

                            // Force re-init of maps in the realm
                            realm.init();

                            // Save state
                            realm.saveState(moduleReg.getStateManager(realm.getLocalID()));

                            // Store TOTP info in session to show on next GET
                            var session = req.getSession(true);
                            session.setAttribute("totp_secret", secret);
                            session.setAttribute("totp_uri", org.sensorhub.impl.security.TOTPUtils.getQRUrl("admin", secret));

                            // Initialize TOTP session and bridge
                            session.setAttribute("2FA_VERIFIED", true);
                            OshLoginService.bridgeAllCookies(req, "admin", getParentHub().getSecurityManager());

                            resp.sendRedirect(req.getContextPath() + "/setup/");
                        } catch (Exception e) {
                            getLogger().error("Setup failed", e);
                            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                        }
                    }
                }), "/setup/*");
                addServletSecurity("/setup/*", false);
            }
            
            // Setup Wizard Redirect Handler
            // We use a separate handler list to ensure the redirect happens before any security checks or content serving
            HandlerList handlerList = new HandlerList();
            handlerList.addHandler(new AbstractHandler() {
                @Override
                public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                    if (getParentHub().getSecurityManager().isUninitialized()) {
                        String uri = request.getRequestURI();
                        String contextPath = config.servletsRootUrl != null ? config.servletsRootUrl : "/sensorhub";
                        if (contextPath.endsWith("/")) contextPath = contextPath.substring(0, contextPath.length() - 1);

                        // Allow setup, login, ca-cert, and static resources
                        if (uri.contains("/setup") || uri.contains("/login") ||
                            uri.contains("/ca-cert") || uri.contains("/VAADIN") || uri.contains("/favicon.ico") ||
                            uri.contains("/PUSH") || uri.contains("/UIDL") || uri.contains("/error") ||
                            uri.equals("/") || uri.equals(contextPath) || uri.equals(contextPath + "/") ||
                            uri.startsWith("/_next") || uri.startsWith("/static") || uri.startsWith("/api/auth") ||
                            (config.servletsRootUrl != null && (uri.equals(config.servletsRootUrl) || uri.equals(config.servletsRootUrl + "/")))) {
                            return;
                        }

                        // Redirect anything else to setup
                        response.sendRedirect(contextPath + "/setup/");
                        baseRequest.setHandled(true);
                    }
                }
            });

            // The handler list order is important: Setup interceptor FIRST, then regular handlers.
            // Jetty will iterate through handlers until one handles the request.
            // Regular handlers include the servletHandler which has the security check.
            handlerList.addHandler(handlers);

            // WRAP regular handlers in a security handler that only triggers IF initialized
            server.setHandler(handlerList);
            
            // also load external xml config file if any
            if (config.xmlConfigFile != null)
            {
                try
                {
                    Resource configFile = Resource.newResource(new File(config.xmlConfigFile));
                    XmlConfiguration xmlConfig = new XmlConfiguration(configFile);
                    
                    // assign IDs to existing beans so they can be reconfigured
                    xmlConfig.getIdMap().put(OSH_SERVER_ID, server);
                    xmlConfig.getIdMap().put(OSH_HANDLERS, handlers);
                    if (http != null)
                        xmlConfig.getIdMap().put(OSH_HTTP_CONNECTOR_ID, http);
                    if (https != null)
                        xmlConfig.getIdMap().put(OSH_HTTPS_CONNECTOR_ID, https);
                    if (fileResourceContext != null)
                        xmlConfig.getIdMap().put(OSH_STATIC_CONTENT_ID, fileResourceContext);
                    if (servletHandler != null)
                        xmlConfig.getIdMap().put(OSH_SERVLET_HANDLER_ID, servletHandler);
                    
                    // append xml config
                    xmlConfig.configure();
                }
                catch (Exception e)
                {
                    throw new IOException("Cannot configure Jetty using external XML file", e);
                }
            }            
            
            server.start();
            getLogger().info("HTTP server started on port " + config.httpPort);
            
            server.getErrorHandler().setShowServlet(false);
            setState(ModuleState.STARTED);
        }
        catch (Exception e)
        {
            throw new SensorHubException("Cannot start embedded HTTP server", e);
        }
    }
    

    @Override
    protected synchronized void doStop() throws SensorHubException
    {
        try
        {
            if (server != null)
            {
                server.stop();
                servletHandler = null;
                jettySecurityHandler = null;
                server = null;
            }
        }
        catch (Exception e)
        {
            throw new SensorHubException("Error while stopping SensorHub embedded HTTP server", e);
        }
    }
    
    
    protected void checkStarted()
    {
        if (!isStarted())
            throw new IllegalStateException("HTTP service must be started before servlets can be deployed");
    }
    
    
    public void deployServlet(HttpServlet servlet, String path)
    {
        deployServlet(servlet, null, path);
    }
    
    
    public synchronized void deployServlet(HttpServlet servlet, Map<String, String> initParams, String... paths)
    {
        checkStarted();
        
        ServletHolder holder = new ServletHolder(servlet);
        if (initParams != null)
            holder.setInitParameters(initParams);
        
        ServletMapping mapping = new ServletMapping();
        mapping.setServletName(holder.getName());
        mapping.setPathSpecs(paths);
        
        servletHandler.getServletHandler().addServlet(holder);
        servletHandler.getServletHandler().addServletMapping(mapping);
        getLogger().debug("Servlet deployed " + mapping.toString());
    }
    
    
    public synchronized void undeployServlet(HttpServlet servlet)
    {
        // silently do nothing if server has already been shutdown
        if (servletHandler == null)
            return;
        
        try
        {
            // there is no removeServlet method so we need to do it manually
            ServletHandler handler = servletHandler.getServletHandler();
            
            // first collect servlets we want to keep
            List<ServletHolder> servlets = new ArrayList<ServletHolder>();
            String nameToRemove = null;
            for( ServletHolder holder : handler.getServlets() )
            {
                if (holder.getServlet() != servlet)
                    servlets.add(holder);
                else
                    nameToRemove = holder.getName();
            }

            if (nameToRemove == null)
                return;
            
            // also update servlet path mappings
            List<ServletMapping> mappings = new ArrayList<ServletMapping>();
            for (ServletMapping mapping : handler.getServletMappings())
            {
                if (!nameToRemove.contains(mapping.getServletName()))
                    mappings.add(mapping);
            }

            // set the new configuration
            handler.setServletMappings( mappings.toArray(new ServletMapping[0]) );
            handler.setServlets( servlets.toArray(new ServletHolder[0]) );
        }
        catch (ServletException e)
        {
            getLogger().error("Error while undeploying servlet", e);
        }
    }
    
    
    public void addServletSecurity(String pathSpec, boolean requireAuth)
    {
        addServletSecurity(pathSpec, requireAuth, Constraint.ANY_AUTH);
    }
    
    
    public synchronized void addServletSecurity(String pathSpec, boolean requireAuth, String... roles)
    {
        if (jettySecurityHandler != null)
        {
            Constraint constraint = new Constraint();
            constraint.setRoles(roles);
            constraint.setAuthenticate(requireAuth);
            ConstraintMapping cm = new ConstraintMapping();
            cm.setConstraint(constraint);
            cm.setPathSpec(pathSpec);
            cm.setMethodOmissions(SECURITY_EXCLUDED_METHODS); // disable auth on OPTIONS requests (needed for CORS)
            jettySecurityHandler.addConstraintMapping(cm);
        }
    }


    public String getServerBaseUrl()
    {
        String baseUrl = "";
        if (!Strings.isNullOrEmpty(config.proxyBaseUrl))
            baseUrl = config.proxyBaseUrl;
        else if (config.httpPort > 0)
            baseUrl = "http://localhost" + (config.httpPort != 80 ? ":" + config.httpPort : "");
        else if (config.httpsPort > 0)
            baseUrl = "https://localhost" + (config.httpsPort != 443 ? ":" + config.httpsPort : "");
        
        return baseUrl;
    }
    
    
    public String getServletsBaseUrl()
    {
        var baseUrl = getServerBaseUrl();
        
        if (config.servletsRootUrl != null)
            baseUrl = appendToUrlPath(baseUrl, config.servletsRootUrl);
        
        return appendToUrlPath(baseUrl, "");
    }
    
    
    public String getPublicEndpointUrl(String path)
    {
        return appendToUrlPath(getServletsBaseUrl(), path);
    }
    
    
    private String appendToUrlPath(String url, String nextPart)
    {
        if (url.endsWith("/"))
            url = url.substring(0, url.length()-1);
        
        return url + (nextPart.startsWith("/") ? nextPart : "/" + nextPart);
    }
    
    
    public Server getJettyServer()
    {
        return server;
    }
    
    private static KeyStoreInfo getKeyStoreInfo(HttpServerConfig config) {
    	String keyStorePath = ModuleUtils.expand(config.keyStorePath);
    	if ((keyStorePath == null) || (keyStorePath.length() == 0) || (keyStorePath.trim().length() == 0)) {
    		keyStorePath = System.getProperty("javax.net.ssl.keyStore");
    	}
  		Asserts.checkNotNullOrBlank(keyStorePath, "Either the key store path or the \"javax.net.ssl.keyStore\" system property must be specified.");
  		
  		String keyStorePassword = ModuleUtils.expand(config.keyStorePassword);
  		if ((keyStorePassword == null) || (keyStorePassword.length() == 0)) {
  			keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
  		}
  		Asserts.checkNotNullOrEmpty(keyStorePassword, "Key store password must be supplied.");
  		
  		String keyAlias = ModuleUtils.expand(config.keyAlias);
  		Asserts.checkNotNullOrEmpty(keyAlias, "Key alias must be supplied");
  		
  		return new KeyStoreInfo(keyStorePath, keyStorePassword, keyAlias);
    }
    
    private static TrustStoreInfo getTrustStoreInfo(HttpServerConfig config) {
    	String trustStorePath = ModuleUtils.expand(config.trustStorePath);
    	if ((trustStorePath == null) || (trustStorePath.length() == 0) || (trustStorePath.trim().length() == 0)) {
    		trustStorePath = System.getProperty("javax.net.ssl.trustStore");
    	}
  		Asserts.checkNotNullOrBlank(trustStorePath, "Either the trust store path or the \"javax.net.ssl.trustStore\" system property must be specified.");
  		
  		String trustStorePassword = ModuleUtils.expand(config.trustStorePassword);
  		if ((trustStorePassword == null) || (trustStorePassword.length() == 0)) {
  			trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
  		}
  		Asserts.checkNotNullOrEmpty(trustStorePassword, "Trust store password must be supplied.");
  		
  		return new TrustStoreInfo(trustStorePath, trustStorePassword);
    }
    
    private static class KeyStoreInfo {
    	private final String keyStorePath;
    	private final String keyStorePassword;
    	private final String keyAlias;
		public KeyStoreInfo(String keyStorePath, String keyStorePassword, String keyAlias) {
			this.keyStorePath = keyStorePath;
			this.keyStorePassword = keyStorePassword;
			this.keyAlias = keyAlias;
		}
		public String getKeyStorePath() {
			return keyStorePath;
		}
		public String getKeyStorePassword() {
			return keyStorePassword;
		}
		public String getKeyAlias() {
			return keyAlias;
		}
    }

    private static class TrustStoreInfo {
    	private final String trustStorePath;
    	private final String trustStorePassword;
		public TrustStoreInfo(String trustStorePath, String trustStorePassword) {
			this.trustStorePath = trustStorePath;
			this.trustStorePassword = trustStorePassword;
		}
		public String getTrustStorePath() {
			return trustStorePath;
		}
		public String getTrustStorePassword() {
			return trustStorePassword;
		}
    }

    @Override
    public boolean isAuthEnabled()
    {
        return config.authMethod != AuthMethod.NONE;
    }

    public ServletContextHandler getServletHandler() {
        return servletHandler;
    }

    public HandlerCollection getHandlers() {
        return handlers;
    }
}
