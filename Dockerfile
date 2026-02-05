# Multi-stage build for optimal image size and security
# Stage 1: Build stage
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B
# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Install wget for health check
RUN apk add --no-cache wget

USER spring:spring
# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar
# Expose application port
EXPOSE 8080

# JVM optimizations and configurations
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]