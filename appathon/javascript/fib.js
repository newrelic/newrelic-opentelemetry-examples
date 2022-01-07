function fibonacci(element) {
  const sequence = [0, 1];
  for (i = 2; i <= element; i++) {
      sequence[i] = sequence[i - 2] + sequence[i - 1];
  }
  return sequence[element];
}

module.exports = fibonacci;