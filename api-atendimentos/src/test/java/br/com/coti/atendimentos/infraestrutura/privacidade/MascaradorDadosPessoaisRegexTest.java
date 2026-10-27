package br.com.coti.atendimentos.infraestrutura.privacidade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MascaradorDadosPessoaisRegexTest {

    private final MascaradorDadosPessoaisRegex mascarador = new MascaradorDadosPessoaisRegex();

    // ---- CPF

    @Test
    void mascaraCpfComPontuacao() {
        assertThat(mascarador.mascarar("meu CPF é 529.982.247-25, posso me matricular?"))
                .isEqualTo("meu CPF é [CPF], posso me matricular?");
    }

    @Test
    void mascaraCpfComPontuacaoMesmoComDigitoVerificadorErrado() {
        assertThat(mascarador.mascarar("cpf 123.456.789-09 aqui")).isEqualTo("cpf [CPF] aqui");
    }

    @Test
    void mascaraCpfSemPontuacaoQuandoODigitoConfere() {
        assertThat(mascarador.mascarar("cpf 52998224725 aqui")).isEqualTo("cpf [CPF] aqui");
    }

    @Test
    void naoMascaraOnzeDigitosCorridosQueNaoSaoCpf() {
        assertThat(mascarador.mascarar("protocolo 12345678901 do pedido")).doesNotContain("[CPF]");
        assertThat(mascarador.mascarar("codigo 11111111111")).doesNotContain("[CPF]");
    }

    // ---- e-mail

    @ParameterizedTest
    @ValueSource(strings = {"maria.silva@gmail.com", "a_b+tag@empresa.com.br", "JOAO@Dominio.ORG"})
    void mascaraEmails(String email) {
        assertThat(mascarador.mascarar("escreva para " + email + " por favor"))
                .isEqualTo("escreva para [EMAIL] por favor");
    }

    // ---- telefone

    @ParameterizedTest
    @ValueSource(strings = {"(11) 91234-5678", "(11) 1234-5678", "11 91234-5678", "11912345678", "+55 11 91234-5678",
            "+5511912345678", "11-91234-5678", "(21)3456-7890"})
    void mascaraTelefones(String telefone) {
        assertThat(mascarador.mascarar("me ligue no " + telefone + " amanhã"))
                .isEqualTo("me ligue no [TELEFONE] amanhã");
    }

    @Test
    void naoMascaraDezDigitosCorridosQueSaoProtocolo() {
        assertThat(mascarador.mascarar("protocolo 1234567890 aberto")).isEqualTo("protocolo 1234567890 aberto");
    }

    // ---- o que não é dado pessoal

    @Test
    void preservaPrecosAnosCargaHorariaEDatas() {
        String texto = "O curso Java custa R$ 1.500,00 em 12x, tem 120 horas, começa em 15/03/2027 e vale até 2027.";

        assertThat(mascarador.mascarar(texto)).isEqualTo(texto);
    }

    @Test
    void preservaPerguntaSemDadoPessoal() {
        String pergunta = "Preciso saber lógica de programação para fazer o curso de Java?";

        assertThat(mascarador.mascarar(pergunta)).isEqualTo(pergunta);
    }

    // ---- combinações

    @Test
    void mascaraTudoNaMesmaPergunta() {
        String saida = mascarador.mascarar("Sou Maria, CPF 529.982.247-25, e-mail maria@x.com.br, tel (11) 91234-5678. "
                + "Posso fazer o curso de Angular?");

        assertThat(saida)
                .isEqualTo("Sou Maria, CPF [CPF], e-mail [EMAIL], tel [TELEFONE]. Posso fazer o curso de Angular?");
        assertThat(saida).doesNotContain("529", "maria@", "91234");
    }

    @Test
    void mascaraVariasOcorrencias() {
        assertThat(mascarador.mascarar("a@b.co e c@d.co, 529.982.247-25 e 111.444.777-35"))
                .isEqualTo("[EMAIL] e [EMAIL], [CPF] e [CPF]");
    }

    @Test
    void ehIdempotente() {
        String uma = mascarador.mascarar("cpf 529.982.247-25 e maria@x.com.br");

        assertThat(mascarador.mascarar(uma)).isEqualTo(uma);
    }

    @Test
    void textoNuloOuVazioPassa() {
        assertThat(mascarador.mascarar(null)).isNull();
        assertThat(mascarador.mascarar("")).isEmpty();
    }

    @Test
    void naoMexeNosCaracteresEspeciaisDoRestoDoTexto() {
        assertThat(mascarador.mascarar("custa $1 e \\ 2 {x} cpf 529.982.247-25"))
                .isEqualTo("custa $1 e \\ 2 {x} cpf [CPF]");
    }
}
