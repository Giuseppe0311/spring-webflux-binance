FROM gradle:jdk17-alpine as builder

WORKDIR /app


COPY ./build.gradle ./settings.gradle ./
COPY ./gradle ./gradle
COPY ./gradlew ./
COPY ./src ./src

RUN apk add --no-cache dos2unix

RUN dos2unix /app/gradlew && chmod +x /app/gradlew

RUN ./gradlew clean build -x test

FROM openjdk:17-jdk-alpine

WORKDIR /app


COPY --from=builder /app/build/libs/binance-0.0.1-SNAPSHOT.jar .


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "binance-0.0.1-SNAPSHOT.jar"]