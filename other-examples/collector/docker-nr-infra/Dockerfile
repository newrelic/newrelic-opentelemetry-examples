FROM node:latest

# Create app directory
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# Install app dependencies
COPY package.json /usr/src/app/
RUN npm install

# Bundle app source
COPY . /usr/src/app

EXPOSE 3000

ENV OTEL_SERVICE_NAME_NODE=docker-app-nr-infra-agent
ENV ZIPKIN_EXPORTER_ENDPOINT=http://otel-collector-container-nr-agent:9411

CMD ["npm", "start"]
