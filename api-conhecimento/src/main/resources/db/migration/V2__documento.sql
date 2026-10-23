-- Documento cadastrado pelo gestor (RF01). É a fonte da verdade sobre o conhecimento; os trechos pesquisáveis
-- ficam em vector_store. O texto normalizado é guardado para permitir reindexação (troca do modelo de embedding).
CREATE TABLE documento (
    id                uuid          PRIMARY KEY,
    titulo            varchar(200)  NOT NULL,
    conteudo          text          NOT NULL,
    fonte             varchar(300)  NOT NULL,
    categoria         varchar(20)   NOT NULL,
    status            varchar(20)   NOT NULL,
    pendencias        varchar(40)[] NOT NULL DEFAULT '{}',
    hash_conteudo     char(64)      NOT NULL,
    quantidade_chunks integer       NOT NULL DEFAULT 0,
    modelo_embedding  varchar(100)  NOT NULL,
    vigente_ate       date,
    cadastrado_por    varchar(100)  NOT NULL,
    cadastrado_em     timestamptz   NOT NULL,

    CONSTRAINT documento_hash_conteudo_uk UNIQUE (hash_conteudo),
    CONSTRAINT documento_categoria_ck CHECK (categoria IN
        ('CURSO', 'PROGRAMA', 'TECNOLOGIA', 'MODALIDADE', 'PRE_REQUISITO', 'INSTITUCIONAL')),
    CONSTRAINT documento_status_ck CHECK (status IN
        ('PROCESSANDO', 'PUBLICADO', 'EM_REVISAO', 'ERRO_PROCESSAMENTO')),
    -- Só documento publicado tem trechos; nos demais estados nenhum trecho permanece gravado.
    CONSTRAINT documento_chunks_ck CHECK (
        (status = 'PUBLICADO' AND quantidade_chunks > 0)
        OR (status <> 'PUBLICADO' AND quantidade_chunks = 0))
);

-- Listagem do gestor: mais recentes primeiro, com filtro por status e categoria.
CREATE INDEX documento_cadastrado_em_idx ON documento (cadastrado_em DESC);
CREATE INDEX documento_status_idx ON documento (status);
CREATE INDEX documento_categoria_idx ON documento (categoria);
