# syntax=docker/dockerfile:1

FROM ruby:3.2.2 as base

WORKDIR /usr/src/app

COPY . .

RUN bundle install

EXPOSE 8080

CMD bundle exec rackup
