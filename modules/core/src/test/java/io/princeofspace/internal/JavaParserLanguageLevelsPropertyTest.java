package io.princeofspace.internal;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.princeofspace.model.JavaLanguageLevel;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
class JavaParserLanguageLevelsPropertyTest {

    @Property
    void toLanguageLevel_matchesFromRelease_for_nonPreviewLevels(@ForAll("supportedReleases") int release) {
        assertThat(JavaParserLanguageLevels.toLanguageLevel(JavaLanguageLevel.of(release)))
                .isEqualTo(JavaParserLanguageLevels.fromRelease(release));
    }

    @Provide
    Arbitrary<Integer> supportedReleases() {
        Set<Integer> releases = new LinkedHashSet<>();
        for (LanguageLevel languageLevel : LanguageLevel.values()) {
            String name = languageLevel.name();
            if (name.endsWith("_PREVIEW")) {
                continue;
            }
            if (name.matches("JAVA_[0-9]+")) {
                int n = Integer.parseInt(name.substring("JAVA_".length()));
                if (n >= JavaParserLanguageLevels.MIN_SUPPORTED_RELEASE) {
                    releases.add(n);
                }
            }
        }
        return Arbitraries.of(releases);
    }
}
