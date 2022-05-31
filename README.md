# Rebellion

Full-stack web application using ktor websockets and kotlin-compose w/ KMM

## Structure

* `shared` contains code shared among frontend and backend;
* `web` contains simple web application wrote in Compose Multiplatform;
* `backend` contains Ktor server with REST API.

## Running application

The application uses docker to spin up a redis instance, nginx load balancer and 3 instances of the server.

```bash
make up
```

## Build distribution package

To create distribution package, execute:

```
./gradlew distZip
```

It builds frontend, backend and packs everything together.
File will be stored in `./backend/build/distributions/backend.zip`


[compose-mpp]: https://www.jetbrains.com/lp/compose-mpp/

[ktor]: https://ktor.io

