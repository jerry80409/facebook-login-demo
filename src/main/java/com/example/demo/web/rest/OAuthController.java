package com.example.demo.web.rest;

import com.example.demo.web.dto.AccessTokenDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Created by jerry on 2018/3/13.
 *
 * @author jerry
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/facebook")
public class OAuthController {

    // 這些設定, 都可以寫在 application.yml 做 global parameters 方便管理。
    static final String FACEBOOK_APP_ID = "FACEBOOK_APP_ID";
    static final String FACEBOOK_APP_SECRET = "FACEBOOK_APP_SECRET";
    static final String REDIRECT_URI = "http://localhost:8888/api/facebook/oauth/callback";
    static final String DIALOG_URL = "https://www.facebook.com/v2.12/dialog/oauth";
    static final String ACCESS_TOKEN_URL = "https://graph.facebook.com/v2.12/oauth/access_token";
    static final String INDEX_PAGE = "http://localhost:8888";

    final Base64.Decoder decoder = Base64.getDecoder();
    final Base64.Encoder encoder = Base64.getEncoder();

    final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 產生 facebook Oauth Login Dialog URL
     * <p>
     * Sample:
     * https://www.facebook.com/v2.12/dialog/oauth?
     * client_id={app-id}
     * &redirect_uri={"https://www.domain.com/login"}
     * &state={"{st=state123abc,ds=123456789}"}
     *
     * @param jwt   自己 server 端的 auth 驗證, 可能是 jwt token 或是某種 session check 機制。
     * @param perms facebook app 的權限, 依據不同的需求產生不同的 oauth,
     *              <a href="https://developers.facebook.com/docs/facebook-login/permissions/">參閱</a>。
     */
    @RequestMapping(value = "/user/login")
    public ResponseEntity generateUserOAuth(@RequestHeader(value = "Authorization") String jwt,
                                            @RequestParam(value = "perms", required = false) List<String> perms)
            throws MalformedURLException, UnsupportedEncodingException {

        // TODO: 2018/3/13 jwt 驗證, server side auth verify

        // facebook login dialog url。
        final HttpUrl baseURL = HttpUrl.get(new URL(DIALOG_URL));

        // 把之後會用到的訊息, 跟 JWT Token 加密後放到 state 變數, 之後會用到
        final String state = jwt + ";" + String.join(",", perms);
        final String encodeState = encoder.encodeToString(state.getBytes("UTF-8"));

        // 依據規格書, 組成 login dialog 的 url
        String loginUrl = baseURL.newBuilder()
                .addQueryParameter("client_id", FACEBOOK_APP_ID)
                .addQueryParameter("client_secret", FACEBOOK_APP_SECRET)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("state", encodeState)
                .build()
                .url()
                .toString();

        log.info("Response facebook login url: [{}]", loginUrl);
        return new ResponseEntity(
                ImmutableMap.builder().put("login", loginUrl).build(),
                HttpStatus.OK);
    }

    /**
     * 接收 facebook user login callback
     * 此端點無法透過 Authorization 做保護, 所以 state 一定要作加密（加簽）, 驗證處理。
     *
     * @param code  facebook oauth response param
     * @param state facebook oauth response param
     * @return
     */
    @RequestMapping(value = "/oauth/callback")
    public ResponseEntity callbackHandler(@RequestParam(value = "code") String code,
                                          @RequestParam(value = "state") String state)
            throws IOException {

        log.info("Received Facebook login callback with code: [{}] and state: [{}]", code, state);

        // decode state
        final String decodeState = new String(decoder.decode(state), "UTF-8");

        // split status
        final String[] statusItems = decodeState.split(";");
        Map<String, String> statusMap = ImmutableMap.<String, String>builder()
                .put("jwt", statusItems[0])
                .put("perms", statusItems[1])
                .build();

        // 有敏感資訊, 設定為 debug 層級
        log.debug("Facebook Login user's jwt: [{}] and request facebook perms is: [{}]",
                statusMap.get("jwt"), statusMap.get("perms"));

        // TODO: 2018/3/13 verify jwt

        // request facebook get user access_tokens
        final HttpUrl baseURL = HttpUrl.get(new URL(ACCESS_TOKEN_URL));
        final HttpUrl accessTokenUrl = baseURL.newBuilder()
                .addQueryParameter("client_id", FACEBOOK_APP_ID)
                .addQueryParameter("client_secret", FACEBOOK_APP_SECRET)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("code", code)
                .build();

        final OkHttpClient restClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(accessTokenUrl)
                .build();
        Response response = restClient.newCall(request).execute();

        // TODO: 2018/3/13 handler request error.

        AccessTokenDTO accessTokenDTO = objectMapper.readValue(response.body().byteStream(), AccessTokenDTO.class);
        log.info("Request: [{}]", accessTokenUrl.toString());
        log.debug("Response: [{}]", accessTokenDTO);

        // TODO: 2018/3/13 insert or update facebook user info.
        log.info("Update Facebook User ...");

        // redirect to index page
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", INDEX_PAGE);
        return new ResponseEntity(headers, HttpStatus.FOUND);
    }

}
