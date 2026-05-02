package io.princeofspace;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class InternalArchitectureTest {

    /**
     * Fully-qualified names of classes allowed to declare {@code public} methods under {@code
     * io.princeofspace.internal}. Align with {@link #internalTypesArePackagePrivateExceptFormattingEngine}
     * ({@code FormattingEngine} is public) plus intentional visitor/context helpers. Nested {@code
     * SilentCloseable} uses JVM nested-class naming ({@code $}).
     */
    private static final Set<String> INTERNAL_PUBLIC_METHOD_OWNER_ALLOWLIST =
            Set.of(
                    "io.princeofspace.internal.FormattingEngine",
                    "io.princeofspace.internal.BraceEnforcer",
                    "io.princeofspace.internal.PrincePrettyPrinterVisitor",
                    "io.princeofspace.internal.LayoutContext",
                    "io.princeofspace.internal.CommentUtils",
                    "io.princeofspace.internal.LayoutContext$SilentCloseable");

    private static final DescribedPredicate<JavaClass> INTERNAL_PUBLIC_SURFACE_OWNER =
            new DescribedPredicate<>("declared in allowlisted internal public-surface class") {
                @Override
                public boolean test(JavaClass owner) {
                    return INTERNAL_PUBLIC_METHOD_OWNER_ALLOWLIST.contains(owner.getFullName());
                }
            };

    @Test
    void onlyFormatterMayDependOnInternalPackage() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..internal..")
                .and()
                .doNotHaveFullyQualifiedName("io.princeofspace.Formatter")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.princeofspace.internal..")
                .check(new ClassFileImporter().importPackages("io.princeofspace"));
    }

    @Test
    void internalTypesArePackagePrivateExceptFormattingEngine() {
        classes()
                .that()
                .resideInAnyPackage("io.princeofspace.internal..")
                .and()
                .doNotHaveSimpleName("FormattingEngine")
                .should()
                .notBePublic()
                .check(new ClassFileImporter().importPackages("io.princeofspace"));
    }

    @Test
    void publicMethodsInInternalPackageAreAllowlisted() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAnyPackage("io.princeofspace.internal..")
                .and()
                .arePublic()
                .and()
                .areDeclaredInClassesThat(not(INTERNAL_PUBLIC_SURFACE_OWNER))
                .should()
                .notBePublic()
                .allowEmptyShould(true)
                .check(new ClassFileImporter().importPackages("io.princeofspace"));
    }
}
