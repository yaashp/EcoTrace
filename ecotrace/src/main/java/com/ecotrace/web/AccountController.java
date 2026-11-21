package com.ecotrace.web;

import com.ecotrace.domain.AppUser;
import com.ecotrace.dto.Dtos.PasswordChangeRequest;
import com.ecotrace.dto.Dtos.ProfileRequest;
import com.ecotrace.dto.Dtos.UserView;
import com.ecotrace.service.AuthService;
import com.ecotrace.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AuthService auth;

    public AccountController(AuthService auth) {
        this.auth = auth;
    }

    @PutMapping
    public UserView updateProfile(HttpServletRequest http, @Valid @RequestBody ProfileRequest request) {
        AppUser user = auth.requireAny(http);
        return UserAdminService.toView(auth.updateProfile(user, request));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(HttpServletRequest http, @Valid @RequestBody PasswordChangeRequest request) {
        AppUser user = auth.requireAny(http);
        auth.changePassword(user, request);
    }
}
