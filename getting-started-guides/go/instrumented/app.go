package main

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"
)

type responseObject struct {
	Message string  `json:"message"`
	Input   *uint64 `json:"input"`
	Output  *uint64 `json:"output"`
}

func handler(
	w http.ResponseWriter,
	r *http.Request,
) {
	num, err := parseNum(r)
	if err != nil {
		log.Print(err.Error())
		createHttpResponse(
			&w,
			http.StatusBadRequest,
			&responseObject{
				Message: "Input is invalid. Provide your number as request parameter (...?num=x)",
				Input:   &num,
				Output:  nil,
			})
		return
	}

	out, err := fibonacci(num)
	if err != nil {
		log.Printf("Given number [%d] is outside of the range -> 1 <= x <= 90", num)
		createHttpResponse(
			&w,
			http.StatusBadRequest,
			&responseObject{
				Message: "Input is outside of the range [1,90]",
				Input:   &num,
				Output:  nil,
			})
		return
	}

	createHttpResponse(
		&w,
		http.StatusBadRequest,
		&responseObject{
			Message: "Fibonacci is calculated successfully.",
			Input:   &num,
			Output:  &out,
		})
}

func parseNum(
	r *http.Request,
) (
	uint64,
	error,
) {
	return strconv.ParseUint(r.URL.Query().Get("num"), 10, 64)
}

func fibonacci(
	n uint64,
) (
	uint64,
	error,
) {
	if n <= 1 || n > 90 {
		return 0, errors.New("invalid input")
	}

	var n2, n1 uint64 = 0, 1
	for i := uint64(2); i < n; i++ {
		n2, n1 = n1, n1+n2
	}

	return n2 + n1, nil
}

func createHttpResponse(
	w *http.ResponseWriter,
	statusCode int,
	res *responseObject,
) {
	(*w).WriteHeader(statusCode)
	payload, _ := json.Marshal(res)
	(*w).Write(payload)
}
