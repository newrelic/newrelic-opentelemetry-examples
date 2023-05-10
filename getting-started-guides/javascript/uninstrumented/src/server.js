// server.js
const express = require("express");
const path = require("path");
const routes = require("./api/routes");

const app = express();

// Serve static files from the public folder
app.use(express.static(path.join(__dirname, "public")));

app.use("/", routes);

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`Server listening on port ${PORT}`);
});
