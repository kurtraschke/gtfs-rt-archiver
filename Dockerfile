# build
FROM maven:3.8-amazoncorretto-17 AS builder
WORKDIR /usr/src/app
COPY pom.xml .
RUN mvn -B -e -C org.apache.maven.plugins:maven-dependency-plugin:3.2.0:go-offline
COPY . .
RUN mvn -B -e verify

# package without maven
FROM amazoncorretto:17
COPY --from=builder /usr/src/app/target/*.jar /app/
