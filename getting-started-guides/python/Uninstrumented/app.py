from flask import Flask, jsonify
import logging
logging.basicConfig(level=logging.DEBUG)

app = Flask(__name__)

@app.route("/fibonacci/<int:x>", strict_slashes=False)
def fibonacci(x):
    array = [0, 1]
    try:
        if x < 1 or x > 90:
            raise ValueError("x must be 1 <= x <= 90.")

        for n in range(2, x + 1):
            array.append(array[n - 1] + array[n - 2])
        logging.info("Compute fibonacci(" + str(x) + ") = " + str(array[x]))
        return jsonify(fibonacci_index=x, fibonacci_number=array[x])
    
    except:
        logging.error("Failed to compute fibonacci(" + str(x) + ")")

app.run(host='0.0.0.0', port=5000)