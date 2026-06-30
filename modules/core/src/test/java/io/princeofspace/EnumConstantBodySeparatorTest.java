package io.princeofspace;

import io.princeofspace.model.FormatterConfig;
import io.princeofspace.model.JavaLanguageLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumConstantBodySeparatorTest {
    @Test
    void separatorsAttachToAnonymousEnumConstantBodies() {
        String source = "package p; enum E { A { void run(){ return; } }, B { void run(){ return; } }; void base(){ return; } }";
        String expected = String.join("\n",
                "package p;",
                "",
                "enum E {",
                "    A {",
                "        void run() {",
                "            return;",
                "        }",
                "    },",
                "    B {",
                "        void run() {",
                "            return;",
                "        }",
                "    };",
                "    void base() {",
                "        return;",
                "    }",
                "}",
                "");

        for (int level : new int[] {8, 17, 21, 25}) {
            Formatter formatter = new Formatter(
                    FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(level)).build());
            String once = formatter.format(source);
            assertThat(once).as("java %s", level).isEqualTo(expected);
            assertThat(formatter.format(once)).as("java %s idempotent", level).isEqualTo(once);
        }
    }
}
