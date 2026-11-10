FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
COPY src ./src
RUN javac -encoding UTF-8 -d out $(find src -name '*.java')

FROM eclipse-temurin:17-jre

WORKDIR /app
ENV BAGORITHM_DATA_DIR=/var/data
ENV BAGORITHM_FRONTEND_DIR=/app/frontend
COPY --from=build /app/out ./out
COPY frontend ./frontend
EXPOSE 8080
CMD ["java", "-cp", "out", "balgorithm.server.BalgorithmServer"]
