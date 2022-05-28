#FROM gradle:7-jdk11 AS build
#COPY --chown=gradle:gradle . /home/gradle/src
#WORKDIR /home/gradle/src
#RUN gradle stage --no-daemon
#
#FROM openjdk:11
#EXPOSE 8080:8080
#RUN mkdir /app
#COPY --from=build /home/gradle/src/backend/build/install/backend /app
#ENTRYPOINT ["sh","/app/bin/backend"]
# ^ HELLA SLOW (make it fast), need to cache gradle deps somehow

FROM openjdk:11
RUN mkdir /app
COPY ./backend/build/install/backend /app
ENTRYPOINT ["sh","/app/bin/backend"]
