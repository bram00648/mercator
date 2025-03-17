package be.dnsbelgium.mercator.vat.domain;

import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@AllArgsConstructor
@Getter
@ToString
@NoArgsConstructor(force = true)
@EqualsAndHashCode
public class PageVisit {

  private final String visitId;
  private final String domainName;

  Instant crawlStarted;
  Instant crawlFinished;

  private final Integer statusCode;

  private final String url;

  private String linkText;

  private final String path;

  /** The raw content found in the URL above. Can be TXT or HTML or XML or ... */
  // TODO: consider renaming this field to reflect that it can be something else than html.
  private final String html;
  private final String bodyText;

  @Setter
  private String security_txt_content = "";

  @Setter
  private Map<String, String> security_txt_response_headers = new HashMap<>();

  @Setter
  private long security_txt_bytes = 0L;

  @Setter
  private String security_txt_url = "";

  @Builder.Default
  private List<String> vatValues = new ArrayList<>();

  public PageVisit(
      String visitId,
      String domainName,
      String url,
      String path,
      Instant crawlStarted,
      Instant crawlFinished,
      int statusCode,
      String bodyText,
      String html,
      List<String> vatValues,
      String security_txt_content,
      long secuirity_txt_bytes,
      String security_txt_url,
      Map<String, String> security_txt_response_headers) {
    this.visitId = visitId;
    this.domainName = domainName;
    this.url  = cleanUp(url, 500);
    this.path = cleanUp(path,500);
    this.crawlStarted = crawlStarted;
    this.crawlFinished = crawlFinished;
    this.statusCode = statusCode;
    this.bodyText = cleanUp(bodyText, 20_000);
    this.vatValues = vatValues;
    // 90% of the .be websites have a html length below 281.187 bytes
    // 61k .be websites have (on the landing page) a html document over 500.000 bytes
    // Their combined html sums up to 50 GB (average of 872k) so we would 'save' 32 GB by truncating to 500k bytes
    // The 1.3 million other websites sum up to 89 GB
    this.html = cleanUp(html, 500_000);
    this.security_txt_content = cleanUp(security_txt_content, 500_000);
    this.security_txt_bytes = secuirity_txt_bytes;
    this.security_txt_url = security_txt_url;
    this.security_txt_response_headers = security_txt_response_headers;
  }

  private String cleanUp(String input, int maxLength) {
    if (input == null) {
      return null;
    }
    if (input.contains("\u0000")) {
      return null;
    }
    return StringUtils.abbreviate(input, maxLength);
  }

  public void setLinkText(String linkText) {
    this.linkText  = cleanUp(linkText, 500);
  }

}
