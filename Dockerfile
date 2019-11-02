FROM openjdk:8-jre-alpine

RUN mkdir /app
COPY ./build/libs/schedge-all.jar /app/schedge-all.jar
WORKDIR /app

CMD ["java", "-jar", "schedge-all.jar", "scrape", "--term", "1198", "--school", "UA"]
# CMD ["java", "-jar", "schedge-all.jar"]
