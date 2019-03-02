package org.onedatashare.server.service.oauth;


import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.onedatashare.server.model.core.Credential;
import org.onedatashare.server.model.credential.OAuthCredential;
import org.onedatashare.server.model.error.DuplicateCredentialException;
import org.onedatashare.server.module.googledrive.GoogleDriveSession;
import org.onedatashare.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Service
public class GoogleDriveOauthService{

    public String key;
    private static String finishURI;
    private static GoogleClientSecrets clientSecrets;
    @Value("${drive_client_id}")
    private String clientid;
    @Value("${drive_client_secret}")
    private String cSecrets;
    @Value("${drive_project_id}")
    private String pid;
    @Value("${drive_auth_uri}")
    private String authuri;
    @Value("${drive_token_uri}")
    private String turi;
    @Value("${drive_auth_provider_x509_cert_url}")
    private String authProvider;
    @Value("${drive_redirect_uris}")
    private String ruri[];

    @Autowired
    private UserService userService;
    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".credentials/ods");
    private static final FileDataStoreFactory DATA_STORE_FACTORY;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final HttpTransport HTTP_TRANSPORT;
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE);

    public static class GoogleDriveConfig {
        public String client_id, client_secret, auth_uri, token_uri, auth_provider_x509_cert_url, project_id;
        public String redirect_uris = "http://127.0.0.1:8080/api/stork/oauth,https://onedatashare.org/api/stork/oauth,http://127.0.0.1:8080/api/stork/oauth,http://localhost:8080/api/stork/oauth,http:///www.onedatashare.org/api/stork/oauth";
        public GoogleDriveConfig() {
            this.client_id = "1093251746493-hga9ltfasf35q9daqrf00rgcu1ocj3os.apps.googleusercontent.com";
            this.client_secret = "8Zsk-F6iP3jyIDVvHV33CkKh";
            this.auth_uri = "https://accounts.google.com/o/oauth2/auth";
            this.token_uri = "https://accounts.google.com/o/oauth2/token";
            this.auth_provider_x509_cert_url = "https://www.googleapis.com/oauth2/v1/certs";
            this.project_id = "onedatashare-1531417250475";
            //this.redirect_uris = ruri;
        }
    }

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        GoogleDriveConfig c = new GoogleDriveConfig();
        List<String> redirect_uris;

        if (c == null || c.client_id == null || c.client_secret == null || c.token_uri == null || c.redirect_uris == null)
            finishURI = null;
        else {
            redirect_uris = Arrays.asList(c.redirect_uris.replaceAll("\\[|\\]|\"|\n","")
                    .trim()
                    .split(","));
            finishURI = redirect_uris.get(0);

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();

            details.setAuthUri(c.auth_uri).setClientId(c.client_id)
                    .setClientSecret(c.client_secret).setRedirectUris(Arrays.asList(finishURI))
                    .setTokenUri(c.token_uri);
            clientSecrets = new GoogleClientSecrets().setInstalled(details);
        }
    }

    private static String getUrl() {
        String url;
        try {
            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow =
                    new GoogleAuthorizationCodeFlow.Builder(
                            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                            .setDataStoreFactory(DATA_STORE_FACTORY)
                            .build();

            AuthorizationCodeRequestUrl authorizationUrl =
                    flow.newAuthorizationUrl().setRedirectUri(finishURI).setState(flow.getClientId());

            url = authorizationUrl.toURL().toString();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return url;
    }

    public synchronized String start() {
        GoogleClientSecrets cs = clientSecrets;
        GoogleClientSecrets.Details dt = cs.getDetails();

        this.key = dt.getClientId();
        if (finishURI == null)
            throw new RuntimeException("Google Drive config missing");
        return getUrl();
    }

    private static String storeCredential(String code) {
        String accessToken;
        //String userId = "user";
        try {
            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow =
                    new GoogleAuthorizationCodeFlow.Builder(
                            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                            .setDataStoreFactory(DATA_STORE_FACTORY)
                            .build();

            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(finishURI).execute();

            accessToken = response.getAccessToken();

            flow.createAndStoreCredential(response, accessToken);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return accessToken;
    }

    public synchronized Mono<OAuthCredential> finish( String token, String cookie) {
        String accessToken = storeCredential(token);
        try {
            Drive service = GoogleDriveSession.getDriveService(accessToken);
            String userId = service.about().get().setFields("user").execute().getUser().getEmailAddress();
            OAuthCredential cred = new OAuthCredential(accessToken);
            cred.name = "GoogleDrive: " + userId;

            return userService.getCredentials(cookie).flatMap(val -> {
                for (Credential value : val.values()) {
                    OAuthCredential oauthVal = ((OAuthCredential) value);
                    if ((oauthVal.name != null && oauthVal.name.equals(cred.name))) { //Checks if the ID already matches
                        return Mono.error(new DuplicateCredentialException());           //Account already exists
                    }
                }
                return Mono.just(cred);
            });
        } catch (Exception e) {
            System.out.println("Runtime exception occurred while finishing initializing Google drive oauth session");
            throw new RuntimeException(e);
        }
    }
}