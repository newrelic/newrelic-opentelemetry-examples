const express = require("express");
const app = express();
const api = require("@opentelemetry/api");


const PORT = process.env.PORT || "8080";

function getLargeValue() {
  result = ""
  for (i = 0; i < 4095; i++) {
    result += "A"
  }
  result += "BBBBBBBBBBBBB"
  return result
}

largeValue = getLargeValue()

app.get("/ping", (_, res) => {
  const currentSpan = api.trace.getSpan(api.context.active());

  // New Relic only accepts attributes values that are less than 4096 characters.
  // When viewing this span in New Relic, the value of the "truncate" attribute will contain no Bs
  currentSpan.setAttribute("truncate", largeValue)

  res.status(200).send("Pong");
});

app.listen(parseInt(PORT, 10), () => {
  console.log(`Listening for requests on http://localhost:${PORT}`);
});
