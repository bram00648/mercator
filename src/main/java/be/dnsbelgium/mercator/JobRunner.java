package be.dnsbelgium.mercator;

import be.dnsbelgium.mercator.batch.BatchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Profile("batch")
@Component
public class JobRunner implements CommandLineRunner {

  private final ApplicationContext context;
  private final BatchConfig batchConfig;
  private final JobLauncher jobLauncher;
  private final Map<String, Job> jobs;
  private static final Logger logger = LoggerFactory.getLogger(JobRunner.class);

  // for some reason IntelliJ does not find this bean
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  public JobRunner(JobLauncher jobLauncher, Map<String, Job> jobs, ApplicationContext context, BatchConfig batchConfig) {
    logger.info("Batch application initialized with {}", batchConfig);
    this.context = context;
    this.jobLauncher = jobLauncher;
    this.batchConfig = batchConfig;
    this.jobs = jobs;
  }


  @Override
  public void run(String... args) throws Exception {
    logger.info("JobRunner.run() started");

    jobs.forEach((name, job) -> {
        try {
          // TODO: do we really need these strings as JobParameters ?
          // why not simply inject them into the Spring components that need them?
          String inputFileParam = "file://" + batchConfig.getInputFile();
          String outputFileParam = "file://" +  batchConfig.outputPathFor(job.getName()).toAbsolutePath();

          JobParameters params = new JobParametersBuilder()
              .addString("inputFile", inputFileParam)
              .addString("outputFile", outputFileParam)
              .addString("job_uuid", UUID.randomUUID().toString())
              .toJobParameters();

          logger.info("Starting job {} with these parameters: {}", name, params);
          JobExecution jobExecution = jobLauncher.run(job, params);
          logger.info("job {} finished with status={} and exitStatus={}",
                  name, jobExecution.getStatus(), jobExecution.getExitStatus().getExitCode());
          logger.info("job {} exit description: {}", name, jobExecution.getExitStatus().getExitDescription());
        } catch (Exception e) {
          logger.atError()
                  .setMessage( "Failed to run job: {}")
                  .addArgument(name)
                  .setCause(e)
                  .log();
        }
    });
    logger.info("All batch jobs executed => exiting");
    SpringApplication.exit(context);
  }


}
