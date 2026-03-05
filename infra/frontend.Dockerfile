FROM eclipse-temurin:25-jdk AS build

WORKDIR /app
COPY . .
RUN ./gradlew :webApp:wasmJsBrowserDistribution --no-daemon

FROM nginx:1.27-alpine

COPY --from=build /app/webApp/build/dist/wasmJs/productionExecutable /usr/share/nginx/html

RUN printf 'server {\n\
    listen 80;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
    # wasm MIME type\n\
    types { application/wasm wasm; }\n\
    include /etc/nginx/mime.types;\n\
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80
