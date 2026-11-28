package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.exception.GlobalExceptionHandler;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.AvatarService;
import com.taskmaster.taskmasterbackend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de regresión de SEG-3 sobre {@link UserController}: el avatar solo se sirve
 * con {@code GET /api/users/me/avatar} (el del usuario autenticado) y la ruta
 * {@code GET /api/users/{id}/avatar} no existe, así que no se puede leer el avatar
 * de otro usuario ni averiguar qué ids existen.
 *
 * <p>Se monta el controlador en modo standalone: sin contexto de Spring ni base de datos.</p>
 *
 * @author Carlos
 */
class UserControllerAvatarTest {

    private static final long ID_USUARIO_AUTENTICADO = 1L;
    private static final byte[] BYTES_AVATAR = {1, 2, 3, 4, 5};

    private AvatarService avatarService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        UserService userService = mock(UserService.class);
        avatarService = mock(AvatarService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);

        UserDetails principal = User.withUsername("usuario-a").password("x").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        when(securityUtils.getUserId(any())).thenReturn(ID_USUARIO_AUTENTICADO);
        when(avatarService.getAvatar(anyLong()))
                .thenReturn(new AvatarService.AvatarData(BYTES_AVATAR, "image/png"));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, avatarService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void miAvatarSeSirveConSuTipoYSinCache() throws Exception {
        mockMvc.perform(get("/api/users/me/avatar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().bytes(BYTES_AVATAR));

        verify(avatarService).getAvatar(ID_USUARIO_AUTENTICADO);
    }

    @Test
    void sinAvatarDevuelve404() throws Exception {
        when(avatarService.getAvatar(ID_USUARIO_AUTENTICADO)).thenReturn(null);

        mockMvc.perform(get("/api/users/me/avatar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAvatarDeOtroUsuarioPorIdNoSeLee() throws Exception {
        mockMvc.perform(get("/api/users/2/avatar"))
                .andExpect(status().isNotFound());

        verify(avatarService, never()).getAvatar(2L);
    }

    @Test
    void laRutaPorIdNoExisteNiParaElPropioUsuario() throws Exception {
        mockMvc.perform(get("/api/users/" + ID_USUARIO_AUTENTICADO + "/avatar"))
                .andExpect(status().isNotFound());

        verify(avatarService, never()).getAvatar(anyLong());
    }
}
