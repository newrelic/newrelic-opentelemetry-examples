const express = require("express");
const path = require("path");
const routes = require("./api/routes");

const Memcached = require('memcached');

const MEMCACHED_CONFIGURATION_ENDPOINT = 'test-memcached.abc123.cfg.use1.cache.amazonaws.com:11211'; // Replace with your Memcached configuration endpoint

 //Memcache connection
 const client= new Memcached(MEMCACHED_CONFIGURATION_ENDPOINT);
 client.connect(MEMCACHED_CONFIGURATION_ENDPOINT, function( err, conn ){
  if( err ) 
  {
    console.log('error connecting to memcached', err);
  }
  else{
    
    console.log( 'conn server',conn.server );
  }
});

client.on('issue', function(details) {
     console.log('issue details',details)
 });

client.set('key', 'value', 10000, function(err) {
  console.log('inside the cb')
   if (err) {
       console.error('Error setting value:', err);
       return;
   }
   console.log('Value set successfully');
});

client.get('key', function(err, data) {
 if (err) {
     console.error('Error getting value:', err);
     return;
 }
 console.log('Value retrieved:', data);
});

const app = express();

// Use the API routes before serving the static files
app.use("/api", routes);

// Serve static files from the public folder
app.use(express.static(path.join(__dirname, "../dist")));

app.get("*", (_, res) => {
  res.sendFile(path.join(__dirname, "../dist", "index.html"));
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Server listening on port ${PORT}`);
});
