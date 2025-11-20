# Imagem base com JDK
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copia código para o container
COPY . /app

# Compila o servidor
RUN javac HubServer.java

# Roda o servidor
CMD ["java", "HubServer"]
