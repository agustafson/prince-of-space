package io.princeofspace;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class InternalArchitectureTest {

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
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("FormattingEngine")
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("BraceEnforcer")
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("PrincePrettyPrinterVisitor")
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("LayoutContext")
                .and()
                .areDeclaredInClassesThat()
                .doNotHaveSimpleName("CommentUtils")
                .should()
                .notBePublic()
                .allowEmptyShould(true)
                .check(new ClassFileImporter().importPackages("io.princeofspace"));
    }
}
