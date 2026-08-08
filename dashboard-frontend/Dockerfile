FROM ghcr.io/cirruslabs/flutter:3.35.0 AS build
ARG API_BASE_URL
ARG GOOGLE_CLIENT_ID
WORKDIR /app
COPY . .
RUN flutter pub get && flutter build web --release --pwa-strategy=none \
    --dart-define=API_BASE_URL=${API_BASE_URL} --dart-define=GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID} --dart-define=AUTH_REQUIRED=true

# Flutter gebruikt standaard een vaste main.dart.js-naam. Geef iedere inhoud een eigen URL,
# zodat browsers nooit een oude app-bundle onder dezelfde naam kunnen blijven serveren.
RUN cd build/web \
 && HASH=$(sha256sum main.dart.js | cut -c1-12) \
 && mv main.dart.js "main.${HASH}.js" \
 && sed -i "s/main\.dart\.js/main.${HASH}.js/g" flutter_bootstrap.js flutter.js \
 && test -z "$(grep -rl 'main\.dart\.js' . || true)"

FROM nginxinc/nginx-unprivileged:1.27-alpine
USER 0
RUN chgrp -R 0 /etc/nginx /usr/share/nginx/html /var/cache/nginx \
 && chmod -R g+rwX /etc/nginx /usr/share/nginx/html /var/cache/nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build/web /usr/share/nginx/html
USER 101
EXPOSE 8080
