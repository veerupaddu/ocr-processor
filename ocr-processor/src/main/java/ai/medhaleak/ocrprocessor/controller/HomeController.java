package ai.medhaleak.ocrprocessor.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;

@Controller
public class HomeController {

    @GetMapping({"/", "/home"})
    public String home(@AuthenticationPrincipal UUID userId, Model model) {
        return "home/index";
    }
}
