package be.dnsbelgium.mercator.dns.persistence;

import be.dnsbelgium.mercator.dns.dto.RecordType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {

    private String id;

    private String visitId;

    private String domainName;

    private String prefix;

    private RecordType recordType;

    private Integer rcode;

    @Builder.Default
    private ZonedDateTime crawlTimestamp = ZonedDateTime.now();

    private boolean ok;

    private String problem;

    @Builder.Default
    private List<Response> responses = new ArrayList<>();

    private int numOfResponses;

    public int getNumOfResponses() {
        return this.responses.size();
    }

}