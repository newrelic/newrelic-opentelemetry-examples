# syntax=docker/dockerfile:1
#
# Unlike getting-started-guides/javascript/Dockerfile, this image runs with
# the New Relic Node.js agent instead of OpenTelemetry's own SDK setup
# (`--require ./instrumentation.js`). It plays "Service A" in the
# "OpenTelemetry + New Relic - Head Sampling" diagram: a service on a New
# Relic agent that is the root of the trace and must align with the OTel
# services downstream. app.js is replaced by an overlay (see app.js) that
# adds an optional upstream call.
ARG NODE_VERSION=24.22.1

FROM node:${NODE_VERSION}-alpine

ENV NODE_ENV production

WORKDIR /usr/src/app

RUN --mount=type=bind,source=package.json,target=package.json \
    --mount=type=bind,source=package-lock.json,target=package-lock.json \
    --mount=type=cache,target=/root/.npm \
    npm ci --omit=dev && npm install --no-save newrelic

COPY . .
COPY --from=overlay app.js ./app.js

USER node

EXPOSE 8080

CMD ["node", "--require", "newrelic", "app.js"]
