package br.com.coti.conhecimento.api.erro;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de códigos de erro estáveis (specs/06-api-contracts.md §2.2).
 * O {@code detalhe} é uma mensagem segura: nunca contém o valor recebido nem detalhe interno.
 */
public enum CodigoErro {
    DADOS_INVALIDOS(HttpStatus.BAD_REQUEST, "Dados inválidos", "A requisição contém campos inválidos."),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Não autenticado", "Credencial ausente ou inválida."),
    ACESSO_NEGADO(HttpStatus.FORBIDDEN, "Acesso negado", "A credencial não tem permissão para esta operação."),
    CONHECIMENTO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Conhecimento não encontrado", "Conhecimento não encontrado."),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso não encontrado", "Recurso não encontrado."),
    METODO_NAO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido", "Método não permitido para este recurso."),
    CONHECIMENTO_DUPLICADO(HttpStatus.CONFLICT, "Conhecimento duplicado", "Já existe um conhecimento com o mesmo conteúdo."),
    CONTEUDO_GRANDE_DEMAIS(HttpStatus.valueOf(413), "Conteúdo grande demais", "O conteúdo excede o tamanho permitido."),
    TIPO_MIDIA_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de mídia não suportado", "Use application/json."),
    DADO_PESSOAL_DETECTADO(HttpStatus.valueOf(422), "Dado pessoal detectado", "O conteúdo contém dados pessoais e não pode ser cadastrado."),
    LIMITE_REQUISICOES(HttpStatus.TOO_MANY_REQUESTS, "Limite de requisições", "Muitas requisições. Tente novamente mais tarde."),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado. Informe o correlacaoId ao suporte."),
    MODELO_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Modelo indisponível", "O serviço de inteligência artificial está temporariamente indisponível."),
    BASE_CONHECIMENTO_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Base de conhecimento indisponível", "A base de conhecimento está temporariamente indisponível.");

    private final HttpStatus status;
    private final String titulo;
    private final String detalhe;

    CodigoErro(HttpStatus status, String titulo, String detalhe) {
        this.status = status;
        this.titulo = titulo;
        this.detalhe = detalhe;
    }

    public HttpStatus status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    public String detalhe() {
        return detalhe;
    }

    /** URI do tipo do problema: {@code urn:coti-ai:problema:<codigo-em-kebab-case>}. */
    public String tipo() {
        return "urn:coti-ai:problema:" + name().toLowerCase().replace('_', '-');
    }

    /** Código genérico para um status HTTP produzido pelo framework (sem valor do cliente). */
    public static CodigoErro paraStatus(int status) {
        return switch (status) {
            case 400 -> DADOS_INVALIDOS;
            case 401 -> NAO_AUTENTICADO;
            case 403 -> ACESSO_NEGADO;
            case 404 -> RECURSO_NAO_ENCONTRADO;
            case 405 -> METODO_NAO_PERMITIDO;
            case 406, 415 -> TIPO_MIDIA_NAO_SUPORTADO;
            case 413 -> CONTEUDO_GRANDE_DEMAIS;
            case 429 -> LIMITE_REQUISICOES;
            default -> status >= 400 && status < 500 ? DADOS_INVALIDOS : ERRO_INTERNO;
        };
    }
}
