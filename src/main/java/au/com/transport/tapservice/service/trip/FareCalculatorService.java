package au.com.transport.tapservice.service.trip;

import au.com.transport.tapservice.entity.trip.FareRule;
import au.com.transport.tapservice.exception.FareNotFoundException;
import au.com.transport.tapservice.repository.trip.FareRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class FareCalculatorService {

    private final FareRuleRepository fareRuleRepository;

    /**
     * Fare for a completed trip between two stops.
     * Result cached by "from|to" key.
     */
//    @Cacheable(value = CacheConfig.FARE_RULES_CACHE, key = "#from + '|' + #to")
    public BigDecimal getFare(String from, String to) {
        log.debug("Cache miss — loading fare from DB: {}→{}", from, to);
        return fareRuleRepository.findByFromStopIdAndToStopId(from.trim(), to.trim())
                .map(FareRule::getFareAmount)
                .orElseThrow(() -> new FareNotFoundException(from, to));
    }

    /**
     * Maximum fare from a given origin — used for INCOMPLETE trips.
     * Result cached by origin stop.
     */
//    @Cacheable(value = CacheConfig.MAX_FARE_CACHE, key = "#fromStop")
    public BigDecimal getMaxFare(String fromStop) {
        log.debug("Cache miss — loading max fare from DB: {}", fromStop);
        return fareRuleRepository.findMaxFareFromStop(fromStop)
                .map(FareRule::getFareAmount)
                .orElseThrow(() -> new FareNotFoundException(fromStop, "ANY"));
    }
}
