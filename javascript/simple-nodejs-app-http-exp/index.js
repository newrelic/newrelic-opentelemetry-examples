const express = require("express");
const app = express();

const PORT = process.env.PORT || "8080";

app.get("/ping", (_, res) => {
  res.status(200).send("Pong");
});

app.listen(parseInt(PORT, 10), () => {
  console.log(`Listening for requests on http://localhost:${PORT}`);
});
