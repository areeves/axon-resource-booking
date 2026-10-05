FROM node:22-alpine AS build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM nginx:alpine

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY src/main/resources/static/index.html /usr/share/nginx/html/index.html
COPY src/main/resources/static/console.css /usr/share/nginx/html/console.css
COPY --from=build /workspace/target/classes/static/app /usr/share/nginx/html/app

EXPOSE 80
