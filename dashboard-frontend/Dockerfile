FROM ghcr.io/cirruslabs/flutter:3.35.0 AS build
ARG API_BASE_URL
ARG GOOGLE_CLIENT_ID
WORKDIR /app
COPY . .
RUN flutter pub get && flutter build web --release --dart-define=API_BASE_URL=${API_BASE_URL} --dart-define=GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID} --dart-define=AUTH_REQUIRED=true
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build/web /usr/share/nginx/html
EXPOSE 8080
