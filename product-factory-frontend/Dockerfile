FROM ghcr.io/cirruslabs/flutter:3.44.0@sha256:46691e311715845de03a3ba4753a475476936805b29431b1f00f1816981033f8 AS build
RUN git -C /sdks/flutter fetch --depth 1 origin ee80f08bbf97172ec030b8751ceab557177a34a6 \
    && git -C /sdks/flutter checkout --detach ee80f08bbf97172ec030b8751ceab557177a34a6 \
    && flutter precache --web \
    && dart --version 2>&1 | grep -q '3.12.2'
ARG APPLICATION_VERSION=0.1.0
ARG SOURCE_REVISION
ARG BUILD_TIME
WORKDIR /source
COPY product-factory-frontend product-factory-frontend
COPY tools tools
RUN PF_APPLICATION_VERSION="${APPLICATION_VERSION}" \
    PF_GIT_REVISION="${SOURCE_REVISION}" \
    PF_BUILD_TIME="${BUILD_TIME}" \
    PF_ENVIRONMENT=production \
    PF_PUBLIC_BACKEND_URL= \
    PF_GOOGLE_CLIENT_ID= \
    tools/build-frontend.sh

FROM nginxinc/nginx-unprivileged:1.29.1-alpine@sha256:27985295bdb22a1ef8f712863210bd5877c0f3006494a593e86b3fe0fa55467e
COPY product-factory-frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /source/product-factory-frontend/build/web /usr/share/nginx/html
USER 101
EXPOSE 8080
