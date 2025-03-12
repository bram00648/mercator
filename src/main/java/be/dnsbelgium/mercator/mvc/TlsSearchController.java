package be.dnsbelgium.mercator.mvc;

import be.dnsbelgium.mercator.persistence.TlsRepository;
import be.dnsbelgium.mercator.tls.domain.TlsCrawlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class TlsSearchController {

  private static final Logger logger = LoggerFactory.getLogger(TlsSearchController.class);

  private final TlsRepository tlsRepository;

  public TlsSearchController(TlsRepository tlsRepository) {
    this.tlsRepository = tlsRepository;
  }

  /**
   * show the most recent TLS crawl result for give domain name
   * @param domainName the domainName to search for
   * @return the view
   */
  @GetMapping("/search/tls/latest")
  public String findLatestResult(Model model, @RequestParam(name = "domainName") String domainName) {
    logger.debug("domainName = {}", domainName);
    model.addAttribute("domainName", domainName);
    Optional<TlsCrawlResult> crawlResult = tlsRepository.findLatestResult(domainName);
    if (crawlResult.isPresent()) {
      model.addAttribute("tlsCrawlResults", List.of(crawlResult.get()));
      logger.debug("We found {} for {}", crawlResult, domainName);
    } else {
      logger.debug("No TLS crawl result found for domainName = {}", domainName);
    }
    return "visit-details-tls";
  }

  @GetMapping("/search/tls/ids")
  public String searchVisitIds(Model model, @RequestParam(name = "domainName") String domainName) {
    logger.info("domainName = {}", domainName);
    model.addAttribute("domainName", domainName);
    List<String> visitIds = tlsRepository.searchVisitIds(domainName);
    model.addAttribute("visitIds", visitIds);
    logger.debug("For {} we found {}", domainName, visitIds);
    return "search-results-tls";
  }

  @GetMapping("/search/tls/id")
  public String findByVisitId(Model model, @RequestParam(name = "visitId") String visitId) {
    logger.info("visitId= {}", visitId);
    model.addAttribute("visitId", visitId);
    Optional<TlsCrawlResult> tlsCrawlResult = tlsRepository.findByVisitId(visitId);
    logger.info(tlsCrawlResult.toString());
    if (tlsCrawlResult.isPresent()) {
      model.addAttribute("tlsCrawlResults", List.of(tlsCrawlResult.get()));
    }
    return "visit-details-tls";
  }


}
