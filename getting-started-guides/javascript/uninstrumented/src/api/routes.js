// routes.js
const express = require("express");
const router = express.Router();

const fibonacci = (n) => {
  if (n < 1 || n > 90) {
    throw new Error("API Error: n must be 1 <= n <= 90.");
  }

  const sequence = [1, 1];
  if (n > 2) {
    for (let i = 2; i < n; i++) {
      sequence.push(sequence[i - 1] + sequence[i - 2]);
    }
  }

  return sequence.slice(0, n);
};

router.get("/fibonacci/:n", (req, res) => {
  const n = parseInt(req.params.n);
  try {
    const result = fibonacci(n);
    res.json({ result });
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
});

// Catch-all route handler
router.use("*", (req, res) => {
  res.status(404).json({ error: "Route not found" });
});

module.exports = router;
