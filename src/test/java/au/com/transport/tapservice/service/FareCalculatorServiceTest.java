package au.com.transport.tapservice.service;

import au.com.transport.tapservice.entity.trip.FareRule;
import au.com.transport.tapservice.exception.FareNotFoundException;
import au.com.transport.tapservice.repository.trip.FareRuleRepository;
import au.com.transport.tapservice.service.trip.FareCalculatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("FareCalculatorService")
@ExtendWith(MockitoExtension.class)
class FareCalculatorServiceTest {

    @Mock
    private FareRuleRepository fareRuleRepository;

    @InjectMocks
    private FareCalculatorService fareCalculatorService;

    private FareRule rule(String from, String to, String amount) {
        FareRule rule = new FareRule();
        rule.setFromStopId(from);
        rule.setToStopId(to);
        rule.setFareAmount(new BigDecimal(amount));
        return rule;
    }

    @Nested
    @DisplayName("getFare() — assignment fares")
    class GetFare {

        @Test
        @DisplayName("Stop1 → Stop2 = $3.25")
        void stop1ToStop2() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop1", "Stop2"))
                .thenReturn(Optional.of(rule("Stop1", "Stop2", "3.25")));

            assertThat(fareCalculatorService.getFare("Stop1", "Stop2"))
                .isEqualByComparingTo("3.25");
        }

        @Test
        @DisplayName("Stop2 → Stop1 = $3.25 — bidirectional same fare")
        void stop2ToStop1Bidirectional() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop2", "Stop1"))
                .thenReturn(Optional.of(rule("Stop2", "Stop1", "3.25")));

            assertThat(fareCalculatorService.getFare("Stop2", "Stop1"))
                .isEqualByComparingTo("3.25");
        }

        @Test
        @DisplayName("Stop2 → Stop3 = $5.50")
        void stop2ToStop3() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop2", "Stop3"))
                .thenReturn(Optional.of(rule("Stop2", "Stop3", "5.50")));

            assertThat(fareCalculatorService.getFare("Stop2", "Stop3"))
                .isEqualByComparingTo("5.50");
        }

        @Test
        @DisplayName("Stop3 → Stop2 = $5.50 — bidirectional same fare")
        void stop3ToStop2Bidirectional() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop3", "Stop2"))
                .thenReturn(Optional.of(rule("Stop3", "Stop2", "5.50")));

            assertThat(fareCalculatorService.getFare("Stop3", "Stop2"))
                .isEqualByComparingTo("5.50");
        }

        @Test
        @DisplayName("Stop1 → Stop3 = $7.30")
        void stop1ToStop3() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop1", "Stop3"))
                .thenReturn(Optional.of(rule("Stop1", "Stop3", "7.30")));

            assertThat(fareCalculatorService.getFare("Stop1", "Stop3"))
                .isEqualByComparingTo("7.30");
        }

        @Test
        @DisplayName("Stop3 → Stop1 = $7.30 — bidirectional same fare")
        void stop3ToStop1Bidirectional() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop3", "Stop1"))
                .thenReturn(Optional.of(rule("Stop3", "Stop1", "7.30")));

            assertThat(fareCalculatorService.getFare("Stop3", "Stop1"))
                .isEqualByComparingTo("7.30");
        }

        @Test
        @DisplayName("unknown route throws FareNotFoundException")
        void unknownRouteThrows() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop1", "StopX"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> fareCalculatorService.getFare("Stop1", "StopX"))
                .isInstanceOf(FareNotFoundException.class)
                .hasMessageContaining("Stop1")
                .hasMessageContaining("StopX");
        }

        @Test
        @DisplayName("leading/trailing whitespace trimmed before lookup")
        void trimsWhitespaceBeforeLookup() {
            when(fareRuleRepository.findByFromStopIdAndToStopId("Stop1", "Stop2"))
                .thenReturn(Optional.of(rule("Stop1", "Stop2", "3.25")));

            assertThat(fareCalculatorService.getFare(" Stop1 ", " Stop2 "))
                .isEqualByComparingTo("3.25");
        }
    }

    @Nested
    @DisplayName("getMaxFare() — INCOMPLETE trip max fares")
    class GetMaxFare {

        @Test
        @DisplayName("max fare from Stop1 = $7.30 (Stop1→Stop3 is most expensive)")
        void maxFareFromStop1() {
            when(fareRuleRepository.findMaxFareFromStop("Stop1"))
                .thenReturn(Optional.of(rule("Stop1", "Stop3", "7.30")));

            assertThat(fareCalculatorService.getMaxFare("Stop1"))
                .isEqualByComparingTo("7.30");
        }

        @Test
        @DisplayName("max fare from Stop2 = $5.50 (Stop2→Stop3 is most expensive)")
        void maxFareFromStop2() {
            when(fareRuleRepository.findMaxFareFromStop("Stop2"))
                .thenReturn(Optional.of(rule("Stop2", "Stop3", "5.50")));

            assertThat(fareCalculatorService.getMaxFare("Stop2"))
                .isEqualByComparingTo("5.50");
        }

        @Test
        @DisplayName("max fare from Stop3 = $7.30 (Stop3→Stop1 is most expensive)")
        void maxFareFromStop3() {
            when(fareRuleRepository.findMaxFareFromStop("Stop3"))
                .thenReturn(Optional.of(rule("Stop3", "Stop1", "7.30")));

            assertThat(fareCalculatorService.getMaxFare("Stop3"))
                .isEqualByComparingTo("7.30");
        }

        @Test
        @DisplayName("unknown origin throws FareNotFoundException")
        void unknownOriginThrows() {
            when(fareRuleRepository.findMaxFareFromStop("StopX"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> fareCalculatorService.getMaxFare("StopX"))
                .isInstanceOf(FareNotFoundException.class)
                .hasMessageContaining("StopX");
        }
    }
}
