package com.secureflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.secureflow", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {
    @ArchTest
    static final ArchRule domainDependsOnlyInward = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "javax.servlet..",
                    "org.hibernate..",
                    "..application..",
                    "..infrastructure..",
                    "..adapter..",
                    "..web..",
                    "..persistence..");

    @ArchTest
    static final ArchRule contextsDoNotDependOnEachOther = noClasses()
            .that()
            .resideInAPackage("..identity.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..task..");

    @ArchTest
    static final ArchRule taskDoesNotDependOnIdentity = noClasses()
            .that()
            .resideInAPackage("..task.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..identity..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..web..", "org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule webDoesNotDependOnPersistence = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "org.springframework.data..");

    @ArchTest
    static final ArchRule entitiesStayInInfrastructure =
            classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule userAdapterImplementsPort = classes()
            .that()
            .haveSimpleName("UserPersistenceAdapter")
            .should()
            .implement(com.secureflow.identity.application.UserRepositoryPort.class);

    @ArchTest
    static final ArchRule taskAdapterImplementsPort = classes()
            .that()
            .haveSimpleName("TaskPersistenceAdapter")
            .should()
            .implement(com.secureflow.task.application.TaskRepositoryPort.class);

    @ArchTest
    static final ArchRule packagesAreAcyclic =
            slices().matching("com.secureflow.(*)..").should().beFreeOfCycles();
}
