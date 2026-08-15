# Flutter 3.44 (Dart 3.12) hoort bij de toolchain waarop pubspec.lock is opgelost en waarop
# .github/workflows/verify.yml analyse en tests draait. 3.35.0 bracht Dart 3.9 mee en kon de
# lock (sdks.dart >=3.10.0-0) niet meer bouwen. Van cirruslabs is 3.44.0 de nieuwste gepubliceerde
# tag; verify.yml staat op 3.44.6, dezelfde Flutter-minor en dezelfde Dart-major.minor.
FROM ghcr.io/cirruslabs/flutter:3.44.0 AS build
ARG API_BASE_URL
ARG GOOGLE_CLIENT_ID
ARG AUTH_REQUIRED=true
ARG ACCEPTANCE_DATASET=false
ARG BUILD_ENVIRONMENT
ARG SOURCE_REVISION
ARG DEPLOYED_AT
WORKDIR /app
COPY . .
RUN flutter pub get && flutter build web --release --pwa-strategy=none \
    --dart-define=API_BASE_URL=${API_BASE_URL} --dart-define=GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID} --dart-define=AUTH_REQUIRED=${AUTH_REQUIRED} \
    --dart-define=ACCEPTANCE_DATASET=${ACCEPTANCE_DATASET} \
    --dart-define=BUILD_ENVIRONMENT=${BUILD_ENVIRONMENT} --dart-define=SOURCE_REVISION=${SOURCE_REVISION} \
    --dart-define=DEPLOYED_AT=${DEPLOYED_AT}

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
