const express = require("express");
const path = require("path");
const routes = require("./api/routes");

const app = express();

// Serve static files from the public folder
app.use(express.static(path.join(__dirname, "../dist")));

// Use the API routes before serving the static files
app.use("/api", routes);

app.get("*", (_, res) => {
  res.sendFile(path.join(__dirname, "../dist", "index.html"));
});

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`Server listening on port ${PORT}`);
});
