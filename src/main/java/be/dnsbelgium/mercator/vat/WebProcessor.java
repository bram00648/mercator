package be.dnsbelgium.mercator.vat;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.vat.domain.WebCrawlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class WebProcessor implements ItemProcessor<VisitRequest, WebCrawlResult> {

  private final WebCrawler webCrawler;
  private static final Logger logger = LoggerFactory.getLogger(WebProcessor.class);

  @Autowired
  WebProcessor(WebCrawler webCrawler) {
    this.webCrawler = webCrawler;
  }

  @Override
  public WebCrawlResult process(@NonNull VisitRequest request) {
    logger.info("request = {}", request);
    WebCrawlResult result = webCrawler.crawl(request);
    logger.info("result = {}", result);
    return result;
  }

}
