package com.bhoomi.paycore.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The React app handles these routes in the browser. When someone reloads or opens a deep link,
 * the server must answer with the app's index.html instead of a 404.
 */
@Controller
public class SpaController {

    @GetMapping({"/playground", "/lab", "/explorer", "/how-it-works", "/signin", "/pay/{id}"})
    public String forwardToApp() {
        return "forward:/index.html";
    }
}
