# Imagem do frontend web de teste (truco-web): a página para jogar contra o bot.
# Usada pelo Render (render.yaml). Para rodar sem Docker: ./mvnw -q package -DskipTests && java -jar truco-web/target/truco-web.jar

# 1. Compila só o truco-web e os módulos de que ele depende. Os testes rodam na CI e no ./mvnw verify, não aqui.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -B -ntp -q -pl truco-web -am package -DskipTests

# 2. Roda o jar com o JRE, numa imagem menor.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/truco-web/target/truco-web.jar truco-web.jar
# O Render informa a porta pela variável PORT; o padrão é para rodar a imagem localmente.
ENV PORT=8080
EXPOSE 8080
# O plano gratuito tem 512 MB: deixa a JVM usar até 75% disso.
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "truco-web.jar"]
