from flask import Flask, jsonify

app = Flask(__name__)

@app.route("/fibonacci/<int:x>", strict_slashes=False)
def fibonacci(x):
    try:
        assert 1 <= x <= 90
        array = [0, 1]
        for n in range(2, x + 1):
            array.append(array[n - 1] + array[n - 2])

        return jsonify(fibonacci_index=x, fibonacci_number=array[x])

    except (ValueError, AssertionError):
        raise ValueError("x must be 1 <= x <= 90.")

app.run(host='0.0.0.0', port=5000)