# api-conhecimento. Contexto de build: esta pasta (o projeto é independente da raiz do monorepo).
# Etapa 1: compila com Maven; as dependências ficam em uma camada própria para aproveitar o cache.
FROM maven:3.9.16-eclipse-temurin-21-noble AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Etapa 2: somente o JRE, executando como usuário sem privilégios.
FROM eclipse-temurin:21.0.12.1_1-jre-noble
RUN groupadd --system --gid 10001 coti \
 && useradd --system --uid 10001 --gid coti --no-create-home --shell /usr/sbin/nologin coti
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/api-conhecimento-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
