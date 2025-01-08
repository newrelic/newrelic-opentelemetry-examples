const http = require('http');

async function bootstrap() {
  const server = http.createServer(function (req, res) {
    // console.log('incoming request: %s %s %s', req.method, req.url, req.headers);
    req.resume();
    req.on('end', function () {
      const pathname = req.url;
      const body = `Echo from server at pathname ${pathname}`
      res.writeHead(200, {
        server: 'metrics-example-server',
        'content-type': 'text/plain',
        'content-length': Buffer.byteLength(body),
      });
      res.end(body);
    });
  });

  server.listen(3000);
  console.log('Server listening on port 3000')
}

bootstrap();