# use a maintained OpenJDK runtime image
FROM eclipse-temurin:8-jre
# use shaded jar produced by maven-shade-plugin
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
