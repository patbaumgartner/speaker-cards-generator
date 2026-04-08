package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.config.DevoxxApiConfig;
import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Spring MVC controller for the main website views.
 *
 * <p>
 * Renders the speaker directory index page using the {@code templates/website/index.html}
 * Thymeleaf template.
 */
@Controller
public class WebsiteController {

	private final SpeakerRepository speakerRepository;

	private final EventConfig eventConfig;

	private final DevoxxApiConfig devoxxApiConfig;

	public WebsiteController(SpeakerRepository speakerRepository, EventConfig eventConfig,
			DevoxxApiConfig devoxxApiConfig) {
		this.speakerRepository = speakerRepository;
		this.eventConfig = eventConfig;
		this.devoxxApiConfig = devoxxApiConfig;
	}

	/**
	 * Renders the speaker directory index page.
	 * @param model Spring MVC model populated with speakers and event configuration
	 * @return Thymeleaf view name {@code website/index}
	 */
	@GetMapping("/")
	public String index(Model model) {
		List<Speaker> speakers = speakerRepository.findAllWithTalksBy();
		model.addAttribute("speakers", speakers);
		model.addAttribute("event", eventConfig);
		model.addAttribute("devoxxApiEnabled", devoxxApiConfig.isDevoxxApiEnabled());
		return "website/index";
	}

}
