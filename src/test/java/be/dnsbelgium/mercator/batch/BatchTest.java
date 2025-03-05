package be.dnsbelgium.mercator.batch;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.feature.extraction.HtmlFeatureExtractor;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.persistence.DuckDataSource;
import be.dnsbelgium.mercator.vat.WebProcessor;
import be.dnsbelgium.mercator.vat.domain.PageVisit;
import be.dnsbelgium.mercator.vat.domain.WebCrawlResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.f4b6a3.ulid.Ulid;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.JsonFileItemWriter;
import org.springframework.batch.item.json.builder.JsonFileItemWriterBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.lang.NonNull;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SuppressWarnings({"SpringJavaInjectionPointsAutowiringInspection", "SpringBootApplicationProperties"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = { "crawler.dns.geoIP.enabled=false" })
public class BatchTest {

  private static final Logger logger = LoggerFactory.getLogger(BatchTest.class);

  @Autowired @Qualifier("webJob") Job webJob;
  @Autowired @Qualifier("tlsJob") Job tlsJob;

  @MockitoBean
  WebProcessor webProcessor;

  @Autowired JobRepository jobRepository;
  @Autowired JobLauncher jobLauncher;
  @Autowired JdbcTransactionManager transactionManager;
  @Autowired ObjectMapper objectMapper;

  @Test
  public void webJob() throws Exception {

    Instant started  = LocalDateTime.of(2024,11,28, 23, 59).toInstant(ZoneOffset.UTC);
    Instant finished = started.plusSeconds(3);

    PageVisit pageVisit1 = PageVisit.builder()
            .visitId("v104")
            .html("<h1>I am a page </h1>")
            .url("https://www.dnsbelgium.be/")
            .bodyText("I am a page")
            .domainName("dnsbelgium.be")
            .statusCode(200)
            .path("/")
            .build();
    PageVisit pageVisit2 = PageVisit.builder()
            .visitId("v104")
            .html("<h1>I am the EN page </h1>")
            .url("https://www.dnsbelgium.be/en")
            .bodyText("I am the EN page")
            .domainName("dnsbelgium.be")
            .statusCode(200)
            .path("/en")
            .build();
    PageVisit pageVisit3 = PageVisit.builder()
            .visitId("v104")
            .html("<h1>I am the contact page </h1>")
            .url("https://www.dnsbelgium.be/en/contact")
            .bodyText("I am the contact page")
            .domainName("dnsbelgium.be")
            .vatValues(List.of("BE0466158640", "BE0841242495"))
            .statusCode(200)
            .path("/en/contact")
            .build();


    HtmlFeatureExtractor htmlFeatureExtractor = new HtmlFeatureExtractor(new SimpleMeterRegistry(), false);
    HtmlFeatures features1 = htmlFeatureExtractor.extractFromHtml("<h1>I am a page </h1>", "https://www.dnsbelgium.be/", "dnsbelgium.be");
    HtmlFeatures features2 = htmlFeatureExtractor.extractFromHtml("<h1>I the EN page </h1>", "https://www.dnsbelgium.be/en", "dnsbelgium.be");
    HtmlFeatures features3 = htmlFeatureExtractor.extractFromHtml("<h1>I the contact page </h1>", "https://www.dnsbelgium.be/en/contact", "dnsbelgium.be");

    WebCrawlResult crawlResult1 = WebCrawlResult.builder()
            .crawlFinished(started)
            .crawlFinished(finished)
            .domainName("dnsbelgium.be")
            .matchingUrl("https://www.dnsbelgium.be/en/contact")
            .vatValues(List.of("BE0466158640", "BE0841242495"))
            .visitedUrls(List.of("https://www.dnsbelgium.be/", "https://www.dnsbelgium.be/en","https://www.dnsbelgium.be/en/contact"))
            .startUrl("https://www.dnsbelgium.be/")
            .htmlFeatures(List.of(features1, features2, features3))
            .pageVisits(List.of(pageVisit1, pageVisit2, pageVisit3))
            .build();
    WebCrawlResult crawlResult2 = WebCrawlResult.builder()
            .crawlFinished(started.plusSeconds(10))
            .crawlFinished(finished.plusSeconds(15))
            .domainName("no-website.org")
            .visitedUrls(List.of())
            .startUrl("https://www.no-website.be/")
            .htmlFeatures(List.of())
            .pageVisits(List.of())
            .build();

    when(webProcessor.process(any(VisitRequest.class)))
            .thenReturn(crawlResult1)
            .thenReturn(crawlResult2);

    run(webJob);

    // Now check the resulting output
    JacksonJsonObjectReader<WebCrawlResult> jsonObjectMarshaller
            = new JacksonJsonObjectReader<>(objectMapper, WebCrawlResult.class);

    jsonObjectMarshaller.open(new PathResource("target/test-outputs/web.json"));
    WebCrawlResult w1 = jsonObjectMarshaller.read();
    WebCrawlResult w2 = jsonObjectMarshaller.read();

    logger.info("w1 = {}", w1);
    logger.info("w2 = {}", w2);

    assertThat(w1).isEqualTo(crawlResult1);
    assertThat(w2).isEqualTo(crawlResult2);
  }

  @Test
  public void tls() {
    logger.info("tlsJob = {}", tlsJob);
    // TODO: add test for tls
  }


  public void run(Job job) throws JobExecutionException {
    String outputFileName = "file:./target/test-outputs/" + job.getName() + ".json";
    logger.info("jobRepository = {}", jobRepository);
    logger.info("jobLauncher = {}", jobLauncher);

    JobParameters jobParameters = new JobParametersBuilder()
            .addString("inputFile", "test-data/visit_requests.csv")
            .addString("outputFile", outputFileName)
            .addString("job_uuid", UUID.randomUUID().toString())
            .toJobParameters();
    JobExecution jobExecution = jobLauncher.run(job, jobParameters);
    logger.info("jobExecution.startTime = {}", jobExecution.getStartTime());
    logger.info("jobExecution.endTime   = {}", jobExecution.getEndTime());
    logger.info("jobExecution.exitstatus = {}", jobExecution.getExitStatus());
    logger.info("jobExecution.lastUpdated = {}", jobExecution.getLastUpdated());
    for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
      logger.info("stepExecution = {}", stepExecution);
    }
    assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
  }

  @Test
  public void simpleJob() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
    logger.info("creating simpleJob");
    List<VisitRequest> requestList = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      requestList.add(new VisitRequest(Ulid.fast().toString(), "abc-" + i +".be"));
    }
    ItemReader<VisitRequest> reader = new ListItemReader<>(requestList);

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    JacksonJsonObjectMarshaller<WebCrawlResult> jsonObjectMarshaller
            = new JacksonJsonObjectMarshaller<>(objectMapper);

    PathResource output = new PathResource("output.json");

    JsonFileItemWriter<WebCrawlResult> itemWriter = new JsonFileItemWriterBuilder<WebCrawlResult>()
            .name("WebCrawlResultWriter")
            .jsonObjectMarshaller(jsonObjectMarshaller)
            .resource(output)
            .build();

    Step step = new StepBuilder("web", jobRepository)
            .<VisitRequest, WebCrawlResult>chunk(10, transactionManager)
            .reader(reader)
            .processor(new MyProcessor())
            .writer(itemWriter)
            .build();

    Job job = new JobBuilder("web", jobRepository)
            .start(step)
            .listener(new ParquetMaker(output))
            .build();

    JobParameters jobParameters = new JobParametersBuilder()
            .addString("job_uuid", UUID.randomUUID().toString())
            .toJobParameters();
    JobExecution jobExecution = jobLauncher.run(job, jobParameters);
    System.out.println("jobExecution = " + jobExecution);


  }

  private static class MyProcessor implements ItemProcessor<VisitRequest, WebCrawlResult> {

    @Override
    public WebCrawlResult process(@NonNull VisitRequest item) {
      logger.info("Processing item = {}", item);
      return WebCrawlResult.builder()
              .crawlStarted(Instant.now())
              .crawlFinished(Instant.now().plusMillis(150))
              .visitId(item.getVisitId())
              .domainName(item.getDomainName())
              .startUrl("www.example.com")
              .build();
    }
  }

  private record ParquetMaker(PathResource inputFile) implements JobExecutionListener {

      @SneakyThrows
      @Override
      public void afterJob(@NonNull JobExecution jobExecution) {
        logger.info("jobExecution = {}", jobExecution);
        logger.info("jobExecution.getExecutionContext() = {}", jobExecution.getExecutionContext());

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
          JdbcClient client = JdbcClient.create(DuckDataSource.memory());
          String copy = String.format("copy (select * from '%s') to 'web-output.parquet'", inputFile.getFile().getAbsolutePath());
          logger.info("copy stmt = {}", copy);
          client.sql(copy).update();
          logger.info("parquet file created");
        }

      }
    }

}
