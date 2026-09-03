package cn.ekko.domain.auth.service;

import cn.ekko.domain.auth.adapter.port.ILoginPort;
import cn.ekko.domain.auth.adapter.repository.IUserAccountRepository;
import cn.ekko.domain.auth.model.entity.UserAccountEntity;
import cn.ekko.types.exception.AppException;
import com.auth0.jwt.JWT;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class WeixinLoginServiceTest {

    private WeixinLoginService service;
    private ILoginPort loginPort;
    private Cache<String, String> openidToken;
    private Cache<String, String> sceneTicket;
    private Cache<String, String> ticketScene;
    private IUserAccountRepository userAccountRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        service = new WeixinLoginService();
        loginPort = mock(ILoginPort.class);
        openidToken = CacheBuilder.newBuilder().build();
        sceneTicket = CacheBuilder.newBuilder().build();
        ticketScene = CacheBuilder.newBuilder().build();
        userAccountRepository = mock(IUserAccountRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        ReflectionTestUtils.setField(service, "loginPort", loginPort);
        ReflectionTestUtils.setField(service, "openidToken", openidToken);
        ReflectionTestUtils.setField(service, "sceneTicket", sceneTicket);
        ReflectionTestUtils.setField(service, "ticketScene", ticketScene);
        ReflectionTestUtils.setField(service, "userAccountRepository", userAccountRepository);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "jwtSecret", "test-secret-at-least-32-characters-long");
    }

    @Test
    void shouldRegisterAccountWithHashedPasswordAndIssueToken() {
        when(userAccountRepository.create(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        String token = service.register(" Ekko_01 ", "ExamplePass123");

        ArgumentCaptor<UserAccountEntity> accountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).create(accountCaptor.capture());
        UserAccountEntity saved = accountCaptor.getValue();
        assertEquals("ekko_01", saved.getUsername());
        assertNotEquals("ExamplePass123", saved.getPasswordHash());
        assertTrue(passwordEncoder.matches("ExamplePass123", saved.getPasswordHash()));
        assertEquals(saved.getUserId(), JWT.decode(token).getSubject());
        assertEquals("account", JWT.decode(token).getClaim("authType").asString());
    }

    @Test
    void shouldLoginAndResolveActiveAccountToken() {
        UserAccountEntity account = account("USR_1001", "ekko_01", "ExamplePass123", 3);
        when(userAccountRepository.queryByUsername("ekko_01")).thenReturn(account);
        when(userAccountRepository.queryByUserId("USR_1001")).thenReturn(account);

        String token = service.login("EKKO_01", "ExamplePass123");

        assertEquals("USR_1001", service.resolveUserId("Bearer " + token));
    }

    @Test
    void shouldRejectWrongPassword() {
        when(userAccountRepository.queryByUsername("ekko_01"))
                .thenReturn(account("USR_1001", "ekko_01", "ExamplePass123", 0));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.login("ekko_01", "wrong-password1"));

        assertEquals("1011", exception.getCode());
    }

    @Test
    void shouldRevokeAccountTokensOnLogout() {
        UserAccountEntity account = account("USR_1001", "ekko_01", "ExamplePass123", 1);
        when(userAccountRepository.queryByUsername("ekko_01")).thenReturn(account);
        when(userAccountRepository.queryByUserId("USR_1001")).thenReturn(account);
        when(userAccountRepository.revokeTokens("USR_1001", 1)).thenReturn(1);
        String token = service.login("ekko_01", "ExamplePass123");

        service.logout("Bearer " + token);

        verify(userAccountRepository).revokeTokens("USR_1001", 1);
    }

    @Test
    void shouldHashNewPasswordAndRevokeExistingTokens() {
        UserAccountEntity account = account("USR_1001", "ekko_01", "ExamplePass123", 2);
        when(userAccountRepository.queryByUsername("ekko_01")).thenReturn(account);
        when(userAccountRepository.queryByUserId("USR_1001")).thenReturn(account);
        when(userAccountRepository.updatePassword(
                org.mockito.ArgumentMatchers.eq("USR_1001"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(2))).thenReturn(1);
        String token = service.login("ekko_01", "ExamplePass123");

        service.changePassword("Bearer " + token, "ExamplePass123", "ChangedPass456");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userAccountRepository).updatePassword(
                org.mockito.ArgumentMatchers.eq("USR_1001"), hashCaptor.capture(), org.mockito.ArgumentMatchers.eq(2));
        assertTrue(passwordEncoder.matches("ChangedPass456", hashCaptor.getValue()));
    }

    @Test
    void shouldIssueAndVerifyLoginToken() {
        openidToken.put("ticket-1", "user-1001");

        String token = service.checkLogin("ticket-1");

        assertEquals("user-1001", service.resolveUserId("Bearer " + token));
    }

    @Test
    void shouldRejectInvalidToken() {
        assertThrows(AppException.class, () -> service.resolveUserId("Bearer invalid"));
    }

    @Test
    void shouldCreateAndCacheSceneBoundTicket() throws Exception {
        when(loginPort.createQrCodeTicket("SCENE-01")).thenReturn("ticket-1");

        String ticket = service.createQrCodeTicket(" scene-01 ");

        assertEquals("ticket-1", ticket);
        assertEquals("ticket-1", sceneTicket.getIfPresent("SCENE-01"));
        assertEquals("SCENE-01", ticketScene.getIfPresent("ticket-1"));
        verify(loginPort).createQrCodeTicket("SCENE-01");
    }

    @Test
    void shouldIssueTokenWhenSceneAndTicketMatch() {
        sceneTicket.put("SCENE-01", "ticket-1");
        ticketScene.put("ticket-1", "SCENE-01");
        openidToken.put("ticket-1", "user-1001");

        String token = service.checkLogin("ticket-1", "scene-01");

        assertEquals("user-1001", service.resolveUserId("Bearer " + token));
    }

    @Test
    void shouldRejectMismatchedSceneAndTicket() {
        sceneTicket.put("SCENE-01", "ticket-1");
        ticketScene.put("ticket-1", "SCENE-01");
        openidToken.put("ticket-1", "user-1001");

        assertNull(service.checkLogin("ticket-1", "SCENE-02"));
    }

    @Test
    void shouldRejectSceneBoundTicketOnLegacyCheckEndpoint() {
        sceneTicket.put("SCENE-01", "ticket-1");
        ticketScene.put("ticket-1", "SCENE-01");
        openidToken.put("ticket-1", "user-1001");

        assertNull(service.checkLogin("ticket-1"));
    }

    @Test
    void shouldWaitUntilWeixinCallbackSavesOpenid() {
        sceneTicket.put("SCENE-01", "ticket-1");
        ticketScene.put("ticket-1", "SCENE-01");

        assertNull(service.checkLogin("ticket-1", "SCENE-01"));
    }

    @Test
    void shouldRejectInvalidSceneStr() {
        assertThrows(AppException.class, () -> service.createQrCodeTicket("scene value with spaces"));
    }

    private UserAccountEntity account(
            String userId, String username, String rawPassword, int tokenVersion) {
        return UserAccountEntity.builder()
                .userId(userId)
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(UserAccountEntity.ENABLED)
                .tokenVersion(tokenVersion)
                .build();
    }
}
