package au.com.transport.tapservice.util;

import au.com.transport.tapservice.CommonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommonUtils")
class CommonUtilsTest {

    @Nested
    @DisplayName("normalize()")
    class Normalize {

        @Test
        @DisplayName("trims leading whitespace")
        void trimsLeading() {
            assertThat(CommonUtils.normalize("  Stop1")).isEqualTo("Stop1");
        }

        @Test
        @DisplayName("trims trailing whitespace")
        void trimsTrailing() {
            assertThat(CommonUtils.normalize("Stop1  ")).isEqualTo("Stop1");
        }

        @Test
        @DisplayName("trims both leading and trailing whitespace")
        void trimsBoth() {
            assertThat(CommonUtils.normalize("  Stop1  ")).isEqualTo("Stop1");
        }

        @Test
        @DisplayName("returns value unchanged when no whitespace")
        void noChangeWhenClean() {
            assertThat(CommonUtils.normalize("Stop1")).isEqualTo("Stop1");
        }

        @Test
        @DisplayName("empty string returns empty string")
        void emptyStringReturnsEmpty() {
            assertThat(CommonUtils.normalize("")).isEqualTo("");
        }

        @Test
        @DisplayName("whitespace-only string returns blank string")
        void whitespaceOnlyReturnsBlank() {
            assertThat(CommonUtils.normalize("   ")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("isNumericPan()")
    class IsNumericPan {

        @Test
        @DisplayName("valid 16-digit PAN returns true")
        void valid16DigitPan() {
            assertThat(CommonUtils.isNumericPan("5500005555555559")).isTrue();
        }

        @Test
        @DisplayName("valid test card PANs return true")
        void validTestPans() {
            assertThat(CommonUtils.isNumericPan("4111111111111111")).isTrue();
            assertThat(CommonUtils.isNumericPan("4000000000000001")).isTrue();
            assertThat(CommonUtils.isNumericPan("4000000000000002")).isTrue();
        }

        @Test
        @DisplayName("PAN with letters returns false")
        void panWithLetters() {
            assertThat(CommonUtils.isNumericPan("INVALIDPAN")).isFalse();
        }

        @Test
        @DisplayName("PAN with alphanumeric characters returns false")
        void alphanumericPan() {
            assertThat(CommonUtils.isNumericPan("4111ABC1111111")).isFalse();
        }

        @Test
        @DisplayName("PAN with special characters returns false")
        void panWithSpecialChars() {
            assertThat(CommonUtils.isNumericPan("4111-1111-1111-1111")).isFalse();
            assertThat(CommonUtils.isNumericPan("4111 1111 1111 1111")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null and empty PAN return false")
        void nullAndEmptyReturnFalse(String pan) {
            assertThat(CommonUtils.isNumericPan(pan)).isFalse();
        }

        @Test
        @DisplayName("blank whitespace PAN returns false")
        void blankPanReturnsFalse() {
            assertThat(CommonUtils.isNumericPan("   ")).isFalse();
        }

        @Test
        @DisplayName("numeric PAN with surrounding spaces returns true — trims before check")
        void numericPanWithSpacesReturnsTrue() {
            assertThat(CommonUtils.isNumericPan(" 5500005555555559 ")).isTrue();
        }
    }
}
