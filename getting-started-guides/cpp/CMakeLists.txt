cmake_minimum_required(VERSION 3.25)
project(RollDiceServer)
# Set C++ standard (e.g., C++17)
set(CMAKE_CXX_STANDARD 17)
set(project_name roll-dice-server)

# Define your project's source files
set(SOURCES
    main.cpp  # Add your source files here
)
# Create an executable target
add_executable(dice-server ${SOURCES})

set(OATPP_ROOT /repos/oatpp)
set(opentelemetry-cpp_DIR /repos/otel-cpp/lib/cmake/opentelemetry-cpp)
find_library(OATPP_LIB NAMES liboatpp.a HINTS "${OATPP_ROOT}/build/src/" NO_DEFAULT_PATH)
if (NOT OATPP_LIB)
  message(SEND_ERROR "Did not find oatpp library ${OATPP_ROOT}/build/src")
endif()
# set the path to the directory containing "oatpp" package configuration files
include_directories(${OATPP_ROOT}/src)

# Use find_package to include OpenTelemetry C++
find_package(opentelemetry-cpp CONFIG REQUIRED NO_DEFAULT_PATH)
find_package(protobuf)
find_package(CURL)

# Link against each OpenTelemetry C++ library
target_link_libraries(dice-server PRIVATE
                      ${OATPP_LIB}
                      ${OPENTELEMETRY_CPP_LIBRARIES})
