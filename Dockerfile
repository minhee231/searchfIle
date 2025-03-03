FROM openjdk:17-jdk-alpine

# 작업 디렉토리 설정
WORKDIR /app

# /app/file 디렉토리 생성
RUN mkdir -p /app/file

# JAR 파일 복사
COPY searchfIle.jar /app/searchfIle.jar

# 애플리케이션 실행
CMD ["java", "-jar", "/app/searchfIle.jar"]