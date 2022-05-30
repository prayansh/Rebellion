FROM gradle:7.4.2-jdk11 as build
WORKDIR /home/gradle/src
COPY build.gradle.kts gradle.properties settings.gradle.kts ./
COPY buildSrc ./buildSrc
RUN mkdir backend
COPY backend/build.gradle.kts ./backend/build.gradle.kts
RUN mkdir web
COPY web/build.gradle.kts ./web/build.gradle.kts
RUN mkdir shared
COPY shared/build.gradle.kts ./shared/build.gradle.kts
RUN gradle dependencies --no-daemon # > /dev/null 2>&1 || true # Swallow any errors?
COPY ./ ./
RUN gradle stage --no-daemon # Create distributable

FROM openjdk:11
RUN mkdir /app
COPY --from=build /home/gradle/src/backend/build/install/backend /app
ENTRYPOINT ["sh","/app/bin/backend"]
