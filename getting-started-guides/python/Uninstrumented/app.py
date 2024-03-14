from flask import Flask, jsonify, request

app = Flask(__name__)

@app.route("/fibonacci")
def fibonacci():
    args = request.args
    x = int(args.get("n"))

    try:
        assert 1 <= x <= 90
        array = [0, 1]
        for n in range(2, x + 1):
            array.append(array[n - 1] + array[n - 2])

        return jsonify(n=x, result=array[x])

    except (ValueError, AssertionError):
        return jsonify({"message": "n must be 1 <= n <= 90."})

app.run(host='0.0.0.0', port=8080)