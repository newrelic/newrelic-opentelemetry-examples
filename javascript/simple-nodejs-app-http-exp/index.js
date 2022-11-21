const express = require("express");
const app = express();
const api = require("@opentelemetry/api");


const PORT = process.env.PORT || "8080";

app.get("/ping", (_, res) => {
  const currentSpan = api.trace.getSpan(api.context.active());

  // Add custom attribute
  currentSpan.setAttribute("my-attribute", "my-attribute-value")

  res.status(200).send("Pong");
});

app.listen(parseInt(PORT, 10), () => {
  console.log(`Listening for requests on http://localhost:${PORT}`);
});
