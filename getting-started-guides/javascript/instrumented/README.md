# Instrumented JavaScript Demo Fibonacci App

This is a simple web application that calculates and visualizes Fibonacci numbers. The app is built using Node.js, Express, and Chart.js.

<img width="918" alt="New Relic JS Fibonacci Demo App" src="https://github.com/matewilk/palinka/assets/6328360/edebbafd-8f12-4161-b64b-b47f28b87ff8">

Requires:

* Node.js 14+
* A New Relic account

## Features

- Calculate the nth Fibonacci number (1 <= n <= 90)
- Display the calculated Fibonacci number
- Visualize the Fibonacci sequence up to the nth number using a line chart

## Architecture

The application is divided into two main parts:

1. **Backend**: A Node.js/Express server that exposes a REST API to calculate Fibonacci numbers.
2. **Frontend**: A browser-based single-page application that utilizes the REST API to fetch Fibonacci numbers and visualize the sequence using Chart.js.

The server-side code is organized in the following manner:

- `server.js`: The main entry point of the server that sets up the Express app and listens for incoming connections.
- `routes.js`: A separate module containing the Express routes for handling the Fibonacci REST API.

The frontend code is organized in the following manner:

- `index.html`: The HTML structure of the single-page application.
- `app.js`: The JavaScript code responsible for handling user input, interacting with the backend REST API, and updating the chart.
- `styles.css`: The CSS file containing the styles for the frontend.

## Installation

1. Clone the repository or download the source code:

```bash
git clone https://github.com/newrelic/newrelic-opentelemetry-examples.git
```

2. Navigate to the project folder:

```bash
cd getting-started-guides/javascript/instrumented
```

3. Install the required dependencies:

```bash
npm install
```

## Usage

1. Start the server:

```bash
npm start
```

2. Open your browser and navigate to [http://localhost:8080](http://localhost:8080) to access the app.

## Open Telemetry for Web

To enable OpenTelemetry instrumentation for the web, the following modules have been installed:

```
npm install @opentelemetry/api \
  @opentelemetry/sdk-trace-web \
  @opentelemetry/sdk-trace-base \
  @opentelemetry/instrumentation \
  @opentelemetry/instrumentation-document-load \
  @opentelemetry/instrumentation-user-interaction \
  @opentelemetry/instrumentation-fetch \
  @opentelemetry/context-zone
```


## License

This project is [MIT](https://opensource.org/licenses/MIT) licensed.
