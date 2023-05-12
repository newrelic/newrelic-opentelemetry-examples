package main

import (
	"net/http"
)

func main() {
	// Serve
	http.Handle("/", http.HandlerFunc(handler))
	http.ListenAndServe(":8080", nil)
}
