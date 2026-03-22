package au.com.transport.tapservice.config;

import au.com.transport.tapservice.entity.trip.FareRule;
import au.com.transport.tapservice.repository.trip.FareRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FareRuleDataLoader implements ApplicationRunner {

    private final FareRuleRepository fareRuleRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (fareRuleRepository.count() > 0) {
            log.info("Fare rules already loaded ({} rules) — skipping seed",
                    fareRuleRepository.count());
            return;
        }
        List<FareRule> rules = List.of(
                rule("Stop1", "Stop2", "3.25"),
                rule("Stop2", "Stop1", "3.25"),
                rule("Stop2", "Stop3", "5.50"),
                rule("Stop3", "Stop2", "5.50"),
                rule("Stop1", "Stop3", "7.30"),
                rule("Stop3", "Stop1", "7.30")
        );


        fareRuleRepository.saveAll(rules);
        log.info("Fare rules seeded: {} rules loaded", rules.size());
    }

    private FareRule rule(String from, String to, String amount) {
        return FareRule.builder()
                .fromStopId(from)
                .toStopId(to)
                .fareAmount(new BigDecimal(amount))
                .build();
    }
}
