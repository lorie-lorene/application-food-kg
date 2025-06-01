# FROM openjdk:17-jdk-slim

# RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# WORKDIR /app

# COPY target/*.jar app.jar

# RUN mkdir -p /app/lucene-index /app/data/images

# RUN chown -R appuser:appgroup /app

# EXPOSE 8080

# USER appuser

# ENTRYPOINT ["java", "-jar", "app.jar"]
FROM openjdk:17-jdk-slim

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copier SEULEMENT le JAR (qui contient déjà tout)
COPY target/*.jar app.jar

# Créer les répertoires
RUN mkdir -p /app/lucene-index /app/data/images /app/logs

# Créer l'utilisateur
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
RUN chown -R appuser:appgroup /app && chmod -R 755 /app

USER appuser
EXPOSE 8080

ENV JAVA_OPTS="-Xmx1g -Xms512m -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1