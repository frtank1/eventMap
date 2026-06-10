FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/eventmap-server/ ./
EXPOSE 8080
CMD ["./bin/eventmap-server"]
