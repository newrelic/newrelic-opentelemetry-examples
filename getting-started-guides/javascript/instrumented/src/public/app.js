import Chart from "chart.js/auto";

// stores the chart instance globally
// so we can destroy it when we need to
let fibonacciChart;

export async function getFibonacci() {
  const n = parseInt(document.getElementById("input-n").value);
  if (isNaN(n) || n < 1 || n > 90) {
    setError("invalid value: n must be 1 <= n <= 90");
  }

  const apiBaseUrl = `${window.location.protocol}//${window.location.hostname}:${window.location.port}`;
  const response = await fetch(`${apiBaseUrl}/api/fibonacci/${n}`);

  const data = await response.json();

  if (data.error) {
    throw new Error(data.error);
  } else {
    document.getElementById("result").innerText = data.result[n - 1];
    initializeChart(data.result);
  }
}

// call getFibonacci() when the page loads
window.addEventListener("DOMContentLoaded", getFibonacci);

document.addEventListener("DOMContentLoaded", () => {
  // call getFibonacci() when the button is clicked
  const calculateButton = document.getElementById("calculate-button");
  calculateButton.addEventListener("click", getFibonacci);

  // clear the error message when the input changes
  const inputElement = document.getElementById("input-n");
  inputElement.addEventListener("input", clearErrorMessage);
});

function setError(message) {
  const inputElement = document.getElementById("input-n");
  const errorMessageElement = document.getElementById("error-message");

  inputElement.classList.add("error");
  errorMessageElement.innerText = message;
}

function clearErrorMessage() {
  const inputElement = document.getElementById("input-n");
  const errorMessageElement = document.getElementById("error-message");

  inputElement.classList.remove("error");
  errorMessageElement.innerText = "";
}

function initializeChart(data) {
  const chartElement = document.getElementById("chart");
  const labels = Array.from({ length: data.length }, (_, i) => i + 1);

  if (fibonacciChart) {
    fibonacciChart.destroy();
  }

  fibonacciChart = new Chart(chartElement, {
    type: "line",
    data: {
      labels: labels,
      datasets: [
        {
          label: "Fibonacci Sequence",
          data: data,
          borderColor: "rgba(75, 192, 192, 1)",
          backgroundColor: "rgba(75, 192, 192, 0.2)",
          borderWidth: 1,
          tension: 0.1,
        },
      ],
    },
    options: {
      scales: {
        x: {
          title: {
            display: true,
            text: "n",
          },
        },
        y: {
          title: {
            display: true,
            text: "Fibonacci Number",
          },
        },
      },
    },
  });
}
