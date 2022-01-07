let fibonacci = require("./fib");
const express = require("express");
const app = express();

const PORT = process.env.PORT || 8080;

app.get("/fibonacci", (req, res) => {
  let n = Number(req.query.n);
  
  if (!n || n < 1 || n > 90) {
    res.status(400).send({"message": "n must be >= 1 and <= 90"});
  } else {
    let fib = fibonacci(n);
  
    res.status(200).send({
      "n": n, 
      "result": fib
    });
  }
});

app.listen(parseInt(PORT, 10), () => {
  console.log(`Listening for requests on http://localhost:${PORT}`);
})