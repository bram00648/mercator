package be.dnsbelgium.mercator.vat.domain;

import be.dnsbelgium.mercator.common.VisitIdGenerator;
import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.persistence.DuckDataSource;
import be.dnsbelgium.mercator.test.ObjectMother;
import be.dnsbelgium.mercator.test.TestUtils;
import be.dnsbelgium.mercator.vat.WebCrawler;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

@SpringBootTest
public class SecurityTxtFinderTest {

    private static final Logger logger = LoggerFactory.getLogger(SecurityTxtFinderTest.class);
    ObjectMother objectMother = new ObjectMother();

    @Mock
    private VatScraper vatScraper;

    @Autowired
    private WebCrawler webCrawler;

    @InjectMocks
    private WebCrawler mockedWebCrawler;
    private final JdbcClient jdbcClient = JdbcClient.create(DuckDataSource.memory());

    @Test
    @Disabled
    // this test makes an internet connection, it needs @Autowired for the webcrawler
    // instead of @InjectMocks because we are not mocking the webcrawler for this test,
    // use method to test if the findSecurityTxt works
    public void testFindSecurityTxt() {
        VisitRequest visitRequest = new VisitRequest(VisitIdGenerator.generate(), "dnsbelgium.be");
        PageVisit updatedVisit = webCrawler.findSecurityTxt(visitRequest);
        assertThat(updatedVisit.getBodyText()).isNotEmpty();
        assertThat(updatedVisit.getHeaders().toString()).isNotEmpty();
        assertThat(updatedVisit.getContentLength()).isGreaterThan(0);
        logger.info(updatedVisit.getBodyText());
        logger.info(updatedVisit.getHtml());
        assertThat(updatedVisit.getBodyText()).isNotEqualTo(updatedVisit.getHtml());
    }

    @Test
    @Disabled // used for testing the domains in tranco_be.paerquet
    public void testFindSecurityTxtOnPopularDomainNames() {
        List<String> domainNames = jdbcClient
                .sql("select domain_name from 'tranco_be.parquet' order by tranco_rank limit 100")
                .query(String.class)
                .list();
        List<PageVisit> pageVisits =  new ArrayList<>();

        for (String domainName : domainNames) {
            VisitRequest visitRequest = new VisitRequest(VisitIdGenerator.generate(), domainName);
            try {
                PageVisit found = webCrawler.findSecurityTxt(visitRequest);
                pageVisits.add(found);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        try {
            TestUtils.jsonWriter()
                    .writeValue(new File("security_txt_top100.json"), pageVisits);
            logger.info("JSON data written to file ");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

    }

    @Test
    public void testFindSecurityTxt_notFound() {
        VisitRequest visitRequest = new VisitRequest("test-id", "invalidpage1234567896.be");
        HttpUrl securityTxtUrl = HttpUrl.parse("https://www.invalidpage1234567896.be/.well-known/security.txt");
        Page page1 = objectMother.page1();
        when(vatScraper.fetchAndParse(securityTxtUrl)).thenReturn(page1);

        PageVisit result = mockedWebCrawler.findSecurityTxt(visitRequest);

        assertThat(result.getStatusCode()).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(400);
        verify(vatScraper, times(1)).fetchAndParse(securityTxtUrl);
    }





}
