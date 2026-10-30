# Build from the repository root: docker build -f crypto-wallet/Dockerfile .
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.repo.local=/root/.m2/repository -pl crypto-wallet -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S soas && adduser -S soas -G soas
WORKDIR /app
COPY --from=build /workspace/crypto-wallet/target/crypto-wallet-1.0.0.jar /app/application.jar
USER soas
ENV JAVA_TOOL_OPTIONS="-Xms48m -Xmx256m"
EXPOSE 8300
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 CMD wget -qO- http://localhost:8300/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-jar","/app/application.jar"]

