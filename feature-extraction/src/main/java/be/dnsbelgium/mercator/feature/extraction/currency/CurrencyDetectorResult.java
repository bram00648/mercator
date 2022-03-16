package be.dnsbelgium.mercator.feature.extraction.currency;

import java.util.Collections;
import java.util.Set;

public class CurrencyDetectorResult {

  private int nbOccurrences;
  private int nbDistinct;
  private Set<String> currencies;

  public CurrencyDetectorResult() {
    this.nbOccurrences = 0;
    this.nbDistinct = 0;
    this.currencies = Collections.emptySet();
  }

  public CurrencyDetectorResult(int nbOccurrences, int nbDistinct, Set<String> currencies) {
    this.nbOccurrences = nbOccurrences;
    this.nbDistinct = nbDistinct;
    this.currencies = currencies;
  }

  public int getNbOccurrences() {
    return nbOccurrences;
  }

  public void setNbOccurrences(int nbOccurrences) {
    this.nbOccurrences = nbOccurrences;
  }

  public int getNbDistinct() {
    return nbDistinct;
  }

  public void setNbDistinct(int nbDistinct) {
    this.nbDistinct = nbDistinct;
  }

  public Set<String> getCurrencies() {
    return currencies;
  }

  public void setCurrencies(Set<String> currencies) {
    this.currencies = currencies;
  }
}
