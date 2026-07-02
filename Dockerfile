# Build stage: resolve dependencies and package the WAR once at image build time.
FROM maven:3.8-jdk-8 AS build
WORKDIR /opt/easybuggy4sb/
COPY pom.xml /opt/easybuggy4sb/pom.xml
RUN mvn dependency:go-offline -B
COPY src /opt/easybuggy4sb/src
COPY catalina.policy /opt/easybuggy4sb/catalina.policy
COPY init.sql /opt/easybuggy4sb/init.sql
RUN mvn package -DskipTests -B

# Runtime stage: run the prebuilt WAR instead of `mvn clean spring-boot:run` on every start.
FROM maven:3.8-jdk-8
RUN apt-get update && apt-get install curl vim tree -y
WORKDIR /opt/easybuggy4sb/
COPY --from=build /opt/easybuggy4sb/target/ROOT.war /opt/easybuggy4sb/ROOT.war
COPY catalina.policy /opt/easybuggy4sb/catalina.policy
COPY init.sql /opt/easybuggy4sb/init.sql
CMD ["java", "-jar", "ROOT.war"]
