# syntax=docker/dockerfile:1

# ---- Build stage ----------------------------------------------------------
# Compile and package the Spring Boot app using the Maven wrapper so the
# build uses the exact Maven version pinned in the repo.
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Copy the wrapper and POM first so dependency resolution is cached in its own
# layer and only re-runs when the build definition changes.
# A BuildKit cache mount on /root/.m2 persists the local Maven repo across builds
# so neither `dependency:go-offline` nor `package` (which resolves dynamic plugin
# dependencies on `src/` changes) ever re-downloads artifacts from Maven Central.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copy sources and build the executable jar. Tests are skipped here; they need a
# Docker daemon for Testcontainers, so they run in CI rather than in the image build.
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B package -DskipTests

# ---- Runtime stage --------------------------------------------------------
# A JRE, not the JDK: this stage only runs `java -jar`, so the compiler and the
# rest of the development tooling would be dead weight in the shipped image.
# Runs as a non-root user for a smaller attack surface.
#
# Note this is a different base image from the build stage, so a machine that
# builds pulls both. That costs ~321 MB once on a builder and saves ~100 MB on
# every pull of the result - worth it when the image is deployed anywhere other
# than the machine that built it.
FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

# Create an unprivileged user to run the application.
RUN groupadd --system spring && useradd --system --gid spring spring

# Copy only the built jar from the build stage.
COPY --from=build /workspace/target/*.jar app.jar

USER spring:spring

EXPOSE 8080

# Let the JVM respect container memory limits.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
