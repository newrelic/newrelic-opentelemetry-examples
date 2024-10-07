FROM ubuntu:24.04
ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update
RUN apt-get install --no-install-recommends --no-install-suggests -y \
    build-essential \
    ca-certificates \
    wget \
    git \
    cmake \
    libcurl4-openssl-dev \
    zlib1g-dev

WORKDIR /repos

RUN git clone https://github.com/oatpp/oatpp.git
RUN git clone https://github.com/open-telemetry/opentelemetry-cpp.git

WORKDIR /repos/oatpp
RUN git checkout 1.3.0-latest
WORKDIR /repos/oatpp/build
RUN cmake ..
RUN make
RUN make install

WORKDIR /repos/opentelemetry-cpp
RUN git checkout v1.16.1
RUN ./ci/install_protobuf.sh
WORKDIR /repos/opentelemetry-cpp/build
RUN cmake -DBUILD_TESTING=OFF -DWITH_OTLP_HTTP=ON ..
RUN cmake --build .
RUN cmake --install . --prefix ../../otel-cpp

WORKDIR /apps/fibonacci
ADD main.cpp .
ADD CMakeLists.txt .
WORKDIR /apps/fibonacci/build
RUN cmake ..
RUN cmake --build .

CMD /apps/fibonacci/build/dice-server
