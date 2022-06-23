package be.dnsbelgium.mercator.tls.domain;

import be.dnsbelgium.mercator.tls.crawler.persistence.entities.ScanResult;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class ScanResultCache {

  private static final Logger logger = getLogger(ScanResultCache.class);

  private final int minimumEntriesPerIp;
  private final double requiredRatio;

  // a normal HashMap would probably suffice since we use our own readWriteLock
  private final Map<String, CacheEntry> mapPerIp = new ConcurrentHashMap<>();

  private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  @Autowired
  public ScanResultCache(
      @Value("${scanResult.cache.minimum.entries.per.ip:10}") int minimumEntriesPerIp,
      @Value("${scanResult.cache.required.ratio:0.9}")double requiredRatio) {
    this.minimumEntriesPerIp = minimumEntriesPerIp;
    this.requiredRatio = requiredRatio;
  }

  @SuppressWarnings("unused")
  public static ScanResultCache withDefaultSettings() {
    return new ScanResultCache(10, 0.9);
  }


  public void evictEntriesOlderThan(Duration duration) {
    readWriteLock.writeLock().lock();
    logger.debug("acquired write lock in evictEntriesOlderThan() method");
    try {
      Instant notBefore = Instant.now().minus(duration);
      logger.info("Evicting entries that older than {}, so after {}", duration, notBefore);
      int entriesBefore = mapPerIp.size();
      mapPerIp.entrySet().removeIf(e -> e.getValue().added.isBefore(notBefore));
      int entriesAfter = mapPerIp.size();
      logger.info("Eviction done. before cache had {} entries, now it has {} entries", entriesBefore, entriesAfter);
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }

  public int size() {
    readWriteLock.readLock().lock();
    try {
      return mapPerIp.size();
    } finally {
      readWriteLock.readLock().unlock();
    }
  }

  public void add(ScanResult scanResult) {
    add(Instant.now(), scanResult);
  }

  public void add(Instant added, ScanResult scanResult) {
    logger.info("Adding to cache: IP = {} : scanResult: {}", scanResult.getIp(), scanResult.summary());
    readWriteLock.writeLock().lock();
    logger.debug("acquired write lock in add() method");
    try {
      String ip = scanResult.getIp();
      CacheEntry entry = mapPerIp.get(ip);
      if (entry == null) {
        logger.debug("First time we see this IP: {}", ip);
        mapPerIp.put(ip, CacheEntry.of(added, scanResult));
      } else {
        String summary = scanResult.summary();
        String majoritySummary = entry.majority.summary();
        if (StringUtils.equals(summary, majoritySummary)) {
          // new ScanResult matches with the majority
          entry.resultsInMajority++;
          entry.totalScanResults++;
          logger.debug("new ScanResult matches with the majority: {}", entry);
        } else {
          // we found a deviant ScanResult => check if it became the majority
          if (entry.resultsInMajority > entry.totalScanResults / 2 + 1) {
            // majority won't change
            entry.totalScanResults++;
            logger.debug("we found a deviant ScanResult but majority config did not change: {}", entry);
          } else {
            // count entries in deviantScanResults that have same summary
            long matchingEntries = entry.deviantScanResults.stream().filter(p -> p.summary().equals(summary)).count();
            if (matchingEntries + 1 > entry.resultsInMajority) {
              // we have a new majority
              CacheEntry newEntry = entry.newMajority(added, scanResult);
              logger.info("we have a new majority: {}", newEntry);
              mapPerIp.put(ip, entry.newMajority(added, scanResult));
            } else {
              logger.debug("we found a deviant ScanResult but majority config did not change: {}", entry);
              entry.totalScanResults++;
            }
          }
        }
      }
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }

  public Optional<ScanResult> find(String ip) {
    readWriteLock.readLock().lock();
    logger.debug("acquired read lock in find() method");
    try {
      CacheEntry match = mapPerIp.get(ip);
      if (match == null) {
        return Optional.empty();
      }
      if (match.totalScanResults < minimumEntriesPerIp) {
        logger.debug("Not enough scans for this IP. required={} in-cache={}", minimumEntriesPerIp, match.totalScanResults);
        return Optional.empty();
      }
      logger.debug("totalScanResults={} resultsInMajority={}", match.totalScanResults, match.resultsInMajority);
      double majorityRatio = (1.0 * match.resultsInMajority) / match.totalScanResults;
      logger.debug("requiredRatio={} actual ratio={}", requiredRatio, majorityRatio);

      if (majorityRatio < requiredRatio) {
        logger.debug("Majority not strong enough: actual={} required={}", majorityRatio, requiredRatio);
        return Optional.empty();
      }
      return Optional.of(match.majority);
    } finally {
      readWriteLock.readLock().unlock();
    }
  }


  @AllArgsConstructor
  private static class CacheEntry {
    private ScanResult majority;

    // TODO: should we keep a simplified (or transient/detached) version of the ScanResult's
    private final List<ScanResult> deviantScanResults = new ArrayList<>();
    private long totalScanResults;
    private long resultsInMajority;
    private final String ip;

    private final Instant added;

    private static CacheEntry of(Instant added, ScanResult scanResult) {
      return new CacheEntry(scanResult, 1, 1, scanResult.getIp(), added);
    }

    private CacheEntry newMajority(Instant added, ScanResult scanResult) {
      String newSummary = scanResult.summary();
      long matchingEntries = deviantScanResults.stream().filter(p -> p.summary().equals(newSummary)).count() + 1;
      CacheEntry entry = new CacheEntry(scanResult, totalScanResults+1, matchingEntries, scanResult.getIp(), added);
      entry.deviantScanResults.clear();
      entry.deviantScanResults.addAll(deviantScanResults.stream().filter(r -> !r.summary().equals(newSummary)).collect(Collectors.toList()));
      entry.deviantScanResults.add(this.majority);
      return entry;
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", CacheEntry.class.getSimpleName() + "[", "]")
          .add("totalScanResults=" + totalScanResults)
          .add("resultsInMajority=" + resultsInMajority)
          .add("majority.summary=" + majority.summary())
          .add("ip='" + ip + "'")
          .toString();
    }

  }
}
