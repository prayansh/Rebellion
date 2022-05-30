# Rebellion

Full-stack web application using ktor websockets and kotlin-compose w/ KMM

## Structure

* `shared` contains code shared among frontend and backend;
* `web` contains simple web application wrote in Compose Multiplatform;
* `backend` contains Ktor server with REST API.

## Running application

To run sample execute:

```
./gradlew run
```

then go with your browser to http://127.0.0.1:8080/.

## Build distribution package

To create distribution package, execute:

```
./gradlew distZip
```

It builds frontend, backend and packs everything together.
File will be stored in `./backend/build/distributions/backend.zip`


[compose-mpp]: https://www.jetbrains.com/lp/compose-mpp/

[ktor]: https://ktor.io

