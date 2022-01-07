FROM golang:1.16-alpine AS base
COPY . /src/
WORKDIR /src
RUN go install ./main.go
CMD ["/go/bin/main"]