FROM gradle:8.5-jdk17 AS build
WORKDIR /home/gradle/project

COPY build.gradle settings.gradle ./
COPY src src

RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy AS prepare
WORKDIR /workspace/app

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash fhiruser
USER fhiruser

COPY --from=prepare /workspace/app/dependencies/ ./
COPY --from=prepare /workspace/app/spring-boot-loader/ ./
COPY --from=prepare /workspace/app/snapshot-dependencies/ ./
COPY --from=prepare /workspace/app/application/ ./

# Default profile for the deployable image; override with -e SPRING_PROFILES_ACTIVE=<dev|sit|prod>
ENV SPRING_PROFILES_ACTIVE=prod

# Consumes JAVA_OPTS (defaults to 75% container RAM) if provided
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--XX:MaxRAMPercentage=75.0} org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8085/ || exit 1

EXPOSE 8085
