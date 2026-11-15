package com.secureflow;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.secureflow", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {
    @ArchTest
    static final ArchRule domainDependsOnlyInward = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "javax.servlet..", "org.hibernate..", "..application..", "..infrastructure..", "..adapter..", "..web..", "..persistence..");

    @ArchTest
    static final ArchRule contextsDoNotDependOnEachOther = noClasses().that().resideInAPackage("..identity.domain..")
            .should().dependOnClassesThat().resideInAPackage("..task..");

    @ArchTest
    static final ArchRule taskDoesNotDependOnIdentity = noClasses().that().resideInAPackage("..task.domain..")
            .should().dependOnClassesThat().resideInAPackage("..identity..");
}
