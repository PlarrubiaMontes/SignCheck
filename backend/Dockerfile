# syntax=docker/dockerfile:1
# ---- Build: compile with the Maven wrapper (tests run in CI, not in the image build) ----
FROM eclipse-temurin:21-jdk-noble AS build
# The Maven wrapper falls back to the .tar.gz distribution when unzip is missing, which would then
# fail the distributionSha256Sum check (that hash is for the .zip). Installing unzip keeps validation on.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests

# ---- Runtime: JRE only, non-root user, Trusted List cache in a volume ----
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system signcheck \
    && useradd --system --gid signcheck --home-dir /app --shell /usr/sbin/nologin signcheck \
    && mkdir -p /app /var/cache/signcheck/tl /var/lib/signcheck/demo-ca \
    && chown -R signcheck:signcheck /app /var/cache/signcheck /var/lib/signcheck
WORKDIR /app
COPY --from=build /build/target/signcheck-backend-*.jar app.jar
USER signcheck
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
VOLUME ["/var/cache/signcheck/tl", "/var/lib/signcheck/demo-ca"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
