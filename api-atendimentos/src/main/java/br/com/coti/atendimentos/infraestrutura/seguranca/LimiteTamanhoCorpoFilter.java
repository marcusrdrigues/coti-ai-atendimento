package br.com.coti.atendimentos.infraestrutura.seguranca;

import br.com.coti.atendimentos.api.erro.CodigoErro;
import br.com.coti.atendimentos.api.erro.CorpoGrandeDemaisException;
import br.com.coti.atendimentos.api.erro.EscritorProblema;
import br.com.coti.atendimentos.infraestrutura.config.PropriedadesAtendimentos;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Recusa corpos acima do limite configurado (padrão 256 KB) antes da desserialização: por
 * {@code Content-Length} quando informado e, nos demais casos, contando os bytes lidos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class LimiteTamanhoCorpoFilter extends OncePerRequestFilter {

    private final long limiteBytes;
    private final EscritorProblema escritor;

    public LimiteTamanhoCorpoFilter(PropriedadesAtendimentos propriedades, EscritorProblema escritor) {
        this.limiteBytes = propriedades.web().limiteCorpoBytes();
        this.escritor = escritor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declarado = request.getContentLengthLong();
        if (declarado > limiteBytes) {
            escritor.escrever(request, response, CodigoErro.CONTEUDO_GRANDE_DEMAIS);
            return;
        }
        chain.doFilter(declarado < 0 ? new RequisicaoLimitada(request, limiteBytes) : request, response);
    }

    /** Requisição sem Content-Length (por exemplo, chunked): falha ao ler além do limite. */
    static final class RequisicaoLimitada extends HttpServletRequestWrapper {

        private final long limite;

        RequisicaoLimitada(HttpServletRequest request, long limite) {
            super(request);
            this.limite = limite;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new FluxoLimitado(super.getInputStream(), limite);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8";
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }

    static final class FluxoLimitado extends ServletInputStream {

        private final ServletInputStream origem;
        private final long limite;
        private long lidos;

        FluxoLimitado(ServletInputStream origem, long limite) {
            this.origem = origem;
            this.limite = limite;
        }

        @Override
        public int read() throws IOException {
            int b = origem.read();
            if (b >= 0) {
                contar(1);
            }
            return b;
        }

        @Override
        public int read(byte[] destino, int inicio, int tamanho) throws IOException {
            int n = origem.read(destino, inicio, tamanho);
            if (n > 0) {
                contar(n);
            }
            return n;
        }

        private void contar(int n) throws CorpoGrandeDemaisException {
            lidos += n;
            if (lidos > limite) {
                throw new CorpoGrandeDemaisException(limite);
            }
        }

        @Override
        public boolean isFinished() {
            return origem.isFinished();
        }

        @Override
        public boolean isReady() {
            return origem.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            origem.setReadListener(listener);
        }
    }
}
