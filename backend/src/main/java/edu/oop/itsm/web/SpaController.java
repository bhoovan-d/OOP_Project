package edu.oop.itsm.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Client-side routes (e.g. /incidents/42) are served index.html; the React router takes over. */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/register", "/incidents/**", "/report", "/queue", "/admin/**", "/dashboard", "/my/**", "/workload"})
    public String index() {
        return "forward:/index.html";
    }
}
