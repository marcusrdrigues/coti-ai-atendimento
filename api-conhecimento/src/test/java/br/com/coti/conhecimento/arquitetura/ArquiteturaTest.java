package br.com.coti.conhecimento.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Regras de arquitetura da api-conhecimento (specs/03-architecture.md §3.1 e Artigo 3). */
@AnalyzeClasses(packages = "br.com.coti.conhecimento", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule dominioNaoConheceFrameworkNemInfraestrutura =
            noClasses().that().resideInAPackage("..dominio..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "tools.jackson..", "com.fasterxml..", "jakarta..",
                            "java.sql..", "javax..", "io.modelcontextprotocol..", "org.postgresql..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule aplicacaoNaoConheceWebNemIaNemBanco =
            noClasses().that().resideInAPackage("..aplicacao..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.ai..", "org.springframework.web..", "org.springframework.jdbc..",
                            "org.springframework.security..", "jakarta.servlet..", "io.modelcontextprotocol..",
                            "org.postgresql..")
                    .allowEmptyShould(true);

    /**
     * {@code api.erro} é a linguagem de erro HTTP comum: a infraestrutura de segurança e de filtros a usa para
     * escrever 401/413 no mesmo formato dos controllers. É a única parte de {@code api} acessível por ela.
     */
    @ArchTest
    static final ArchRule direcaoDasDependencias = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Dominio").definedBy("..dominio..")
            .layer("Aplicacao").definedBy("..aplicacao..")
            .layer("ErroHttp").definedBy("..api.erro..")
            .layer("Api").definedBy("..api.rest..", "..api.mcp..")
            .layer("Infraestrutura").definedBy("..infraestrutura..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infraestrutura").mayNotBeAccessedByAnyLayer()
            .whereLayer("ErroHttp").mayOnlyBeAccessedByLayers("Api", "Infraestrutura")
            .whereLayer("Aplicacao").mayOnlyBeAccessedByLayers("Api", "Infraestrutura", "ErroHttp");
}
