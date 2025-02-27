package be.dnsbelgium.mercator.mvc;

import be.dnsbelgium.mercator.mvc.repository.SearchRepository;
import be.dnsbelgium.mercator.vat.domain.WebCrawlResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.bind.annotation.GetMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class SearchController {

  private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
  private final SearchRepository searchRepository;

  public SearchController(SearchRepository searchRepository) {
    this.searchRepository = searchRepository;
  }

  @GetMapping("/search")
  public String search(Model model, @RequestParam(name = "domainName", defaultValue = "") String domainName) {
    if (domainName.isEmpty()) {
      return "search";
    }
    logger.info("search for [{}]", domainName);

    List<WebCrawlResult> webCrawlResults = searchRepository.searchVisitIds(domainName);
    logger.info("our results: " + webCrawlResults);

    model.addAttribute("search", domainName);
    model.addAttribute("visits", webCrawlResults);

    return "search-results";
  }
  @GetMapping("/visits")
  public String visit(Model model, @RequestParam(name = "visitId", defaultValue = "") String visitId, @RequestParam(name = "option", defaultValue = "") String option) {
    logger.info("/visits/{}", visitId);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    model.addAttribute("visitId", visitId);

    List<WebCrawlResult> webCrawlResults = null;
    model.addAttribute("webCrawlResults", webCrawlResults);

    Optional<String> json = searchRepository.searchVisitIdWithOption(visitId, option);
    if (json.isPresent()) {
      try {
        WebCrawlResult webCrawlResult = objectMapper.readValue(json.get(), WebCrawlResult.class);
        model.addAttribute("webCrawlResults", List.of(webCrawlResult));
        logger.info("webCrawlResult = {}", webCrawlResult);

      } catch (JsonProcessingException e) {
        logger.error(e.getMessage());
      }
    }
    return "visit-details";
  }

}
