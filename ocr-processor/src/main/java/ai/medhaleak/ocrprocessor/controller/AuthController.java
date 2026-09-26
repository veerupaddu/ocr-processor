package ai.medhaleak.ocrprocessor.controller;

import ai.medhaleak.ocrprocessor.dto.ChangePasswordRequest;
import ai.medhaleak.ocrprocessor.dto.LoginRequest;
import ai.medhaleak.ocrprocessor.dto.RegisterRequest;
import ai.medhaleak.ocrprocessor.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest("", "", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest registerRequest,
                           BindingResult result, RedirectAttributes redirectAttrs, Model model) {
        if (!registerRequest.password().equals(registerRequest.confirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (result.hasErrors()) {
            model.addAttribute("registerRequest", registerRequest);
            return "auth/register";
        }
        try {
            userService.register(registerRequest);
            redirectAttrs.addFlashAttribute("successMessage", "Account created. Please log in.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("registerRequest", registerRequest);
            return "auth/register";
        }
    }

    @GetMapping("/login")
    public String loginForm() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginRequest loginRequest,
                        BindingResult result, HttpServletResponse response, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("errorMessage", "Please fill in all fields");
            return "auth/login";
        }
        try {
            String token = userService.login(loginRequest);
            Cookie cookie = new Cookie("jwt", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(8 * 3600);
            response.addCookie(cookie);
            return "redirect:/home";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/login";
    }

    @GetMapping("/profile/change-password")
    public String changePasswordForm(Model model) {
        model.addAttribute("changePasswordRequest", new ChangePasswordRequest("", "", ""));
        return "auth/change-password";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@AuthenticationPrincipal UUID userId,
                                  @Valid @ModelAttribute ChangePasswordRequest changePasswordRequest,
                                  BindingResult result, RedirectAttributes redirectAttrs, Model model) {
        if (!changePasswordRequest.newPassword().equals(changePasswordRequest.confirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (result.hasErrors()) {
            model.addAttribute("changePasswordRequest", changePasswordRequest);
            return "auth/change-password";
        }
        try {
            userService.changePassword(userId, changePasswordRequest);
            redirectAttrs.addFlashAttribute("successMessage", "Password changed successfully");
            return "redirect:/profile/change-password";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("changePasswordRequest", changePasswordRequest);
            return "auth/change-password";
        }
    }
}
