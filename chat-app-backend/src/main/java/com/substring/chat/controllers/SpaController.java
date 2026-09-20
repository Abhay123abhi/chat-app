package com.substring.chat.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping("/chat")
    public String chatPage() {
        return "forward:/index.html";
    }
}
