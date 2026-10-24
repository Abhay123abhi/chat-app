package com.substring.chat.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serve the React Router deep link in the single-service production image. */
@Controller
public class PublicSpaController {
    @GetMapping("/chat")
    public String chatPage() {
        return "forward:/index.html";
    }
}
