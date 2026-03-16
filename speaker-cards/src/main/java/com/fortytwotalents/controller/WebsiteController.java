package com.fortytwotalents.controller;

import com.fortytwotalents.config.EventConfig;
import com.fortytwotalents.model.Speaker;
import com.fortytwotalents.repository.SpeakerRepository;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Spring MVC controller for the main website views.
 *
 * <p>Renders the speaker directory index page using the {@code templates/website/index.html}
 * Thymeleaf template.
 */
@Controller
public class WebsiteController {

  private final SpeakerRepository speakerRepository;
  private final EventConfig eventConfig;

  public WebsiteController(SpeakerRepository speakerRepository, EventConfig eventConfig) {
    this.speakerRepository = speakerRepository;
    this.eventConfig = eventConfig;
  }

  /**
   * Renders the speaker directory index page.
   *
   * @param model Spring MVC model populated with speakers and event configuration
   * @return Thymeleaf view name {@code website/index}
   */
  @GetMapping("/")
  public String index(Model model) {
    List<Speaker> speakers = speakerRepository.findAll();
    model.addAttribute("speakers", speakers);
    model.addAttribute("event", eventConfig);
    return "website/index";
  }
}
