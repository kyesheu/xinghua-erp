FROM eclipse-temurin:21-jre

WORKDIR /app

COPY ./target/erp-api-4.1.0.jar erp-api.jar

CMD ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "erp-api.jar"]