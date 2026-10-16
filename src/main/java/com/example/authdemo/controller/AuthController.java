package com.example.authdemo.controller;

import com.example.authdemo.model.User;
import com.example.authdemo.service.LoginResult;
import com.example.authdemo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    /** Session attribute that marks a user as logged in. */
    public static final String SESSION_USER = "AUTH_USER";

    private static final Logger log = LoggerFactory.getLogger("AUTH");

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        return isLoggedIn(session) ? "redirect:/dashboard" : "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (isLoggedIn(session)) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam(required = false) String username,
                          @RequestParam(required = false) String password,
                          HttpSession session,
                          HttpServletRequest request,
                          Model model) {

        LoginResult result = userService.authenticate(username, password);

        // Passwords are intentionally NOT hashed and are written to the log in plain text.
        switch (result) {
            case USERNAME_NOT_FOUND -> {
                log.warn("LOGIN FAILED (wrong username) | username='{}' password='{}'",
                        username, password);
                model.addAttribute("error", "Wrong username. Please check your username and try again.");
                model.addAttribute("username", username);
                return "login";
            }
            case WRONG_PASSWORD -> {
                log.warn("LOGIN FAILED (wrong password) | username='{}' password='{}'",
                        username, password);
                model.addAttribute("error", "Wrong password. Please check your password and try again.");
                model.addAttribute("username", username);
                return "login";
            }
            case SUCCESS -> {
                User user = userService.findByUsername(username);
                session.setAttribute(SESSION_USER, user.getUsername());
                session.setAttribute("AUTH_FULLNAME", user.getFullName());
                log.info("LOGIN SUCCESS | username='{}' password='{}'",
                        username, password);
                return "redirect:/dashboard";
            }
            default -> {
                return "login";
            }
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        model.addAttribute("username", session.getAttribute(SESSION_USER));
        model.addAttribute("fullName", session.getAttribute("AUTH_FULLNAME"));
        return "dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session, HttpServletRequest request) {
        Object user = session.getAttribute(SESSION_USER);
        if (user != null) {
            log.info("LOGOUT | username='{}'", user);
        }
        session.invalidate();
        return "redirect:/login?loggedOut";
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute(SESSION_USER) != null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
