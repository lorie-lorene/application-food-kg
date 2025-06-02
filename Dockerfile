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

# Copier le JAR
COPY target/*.jar app.jar

# COPIER LES IMAGES depuis le bon répertoire local
COPY data/images/ /app/data/images/

# Créer les autres répertoires
RUN mkdir -p /app/lucene-index /app/logs

# Reste de votre configuration...
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
RUN chown -R appuser:appgroup /app && chmod -R 755 /app
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-Xmx1g -Xms512m -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
