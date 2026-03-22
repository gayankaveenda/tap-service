package au.com.transport.tapservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PanTokeniser")
class PanTokeniserTest {

    private PanTokeniser tokeniser;

    @BeforeEach
    void setUp() {
        tokeniser = new PanTokeniser("test-salt");
    }

    @Nested
    @DisplayName("hash()")
    class Hash {

        @Test
        @DisplayName("same PAN always produces same hash — deterministic")
        void isDeterministic() {
            String h1 = tokeniser.hash("5500005555555559");
            String h2 = tokeniser.hash("5500005555555559");
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("different PANs produce different hashes")
        void differentPansProduceDifferentHashes() {
            String h1 = tokeniser.hash("5500005555555559");
            String h2 = tokeniser.hash("4111111111111111");
            assertThat(h1).isNotEqualTo(h2);
        }

        @Test
        @DisplayName("hash is 64 hex characters — SHA-256 output")
        void hashIs64Chars() {
            assertThat(tokeniser.hash("5500005555555559")).hasSize(64);
        }

        @Test
        @DisplayName("hash is lowercase hex only")
        void hashIsLowercaseHex() {
            String hash = tokeniser.hash("5500005555555559");
            assertThat(hash).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("different salts produce different hashes for the same PAN")
        void differentSaltProducesDifferentHash() {
            PanTokeniser otherSalt = new PanTokeniser("different-salt");
            assertThat(tokeniser.hash("5500005555555559"))
                    .isNotEqualTo(otherSalt.hash("5500005555555559"));
        }

        @Test
        @DisplayName("leading and trailing whitespace is trimmed before hashing")
        void trimsWhitespaceBeforeHashing() {
            assertThat(tokeniser.hash("  5500005555555559  "))
                    .isEqualTo(tokeniser.hash("5500005555555559"));
        }

        @Test
        @DisplayName("raw PAN is not present in hash output")
        void rawPanNotInHash() {
            String hash = tokeniser.hash("5500005555555559");
            assertThat(hash).doesNotContain("5500005555555559");
        }
    }

    @Nested
    @DisplayName("mask()")
    class Mask {

        @Test
        @DisplayName("standard 16-digit PAN masked to ****XXXX")
        void masks16DigitPan() {
            assertThat(tokeniser.mask("5500005555555559")).isEqualTo("****5559");
        }

        @Test
        @DisplayName("different PANs produce different masks")
        void differentPansDifferentMask() {
            assertThat(tokeniser.mask("5500005555555559")).isEqualTo("****5559");
            assertThat(tokeniser.mask("4111111111111111")).isEqualTo("****1111");
        }

        @Test
        @DisplayName("PAN with exactly 4 digits masked to ****XXXX")
        void exactlyFourDigits() {
            assertThat(tokeniser.mask("1234")).isEqualTo("****1234");
        }

        @Test
        @DisplayName("null PAN returns ****")
        void nullPanReturnsFourStars() {
            assertThat(tokeniser.mask(null)).isEqualTo("****");
        }

        @Test
        @DisplayName("PAN shorter than 4 digits returns ****")
        void shortPanReturnsFourStars() {
            assertThat(tokeniser.mask("123")).isEqualTo("****");
            assertThat(tokeniser.mask("")).isEqualTo("****");
            assertThat(tokeniser.mask("  ")).isEqualTo("****");
        }

        @Test
        @DisplayName("leading and trailing whitespace trimmed before masking")
        void trimsWhitespaceBeforeMasking() {
            assertThat(tokeniser.mask(" 4000000000000006 ")).isEqualTo("****0006");
        }

        @Test
        @DisplayName("mask always starts with ****")
        void maskAlwaysStartsWithStars() {
            assertThat(tokeniser.mask("5500005555555559")).startsWith("****");
        }

        @Test
        @DisplayName("raw PAN digits not visible in mask except last 4")
        void rawPanNotFullyVisible() {
            String masked = tokeniser.mask("5500005555555559");
            assertThat(masked).doesNotContain("5500005555555");
        }
    }
}
