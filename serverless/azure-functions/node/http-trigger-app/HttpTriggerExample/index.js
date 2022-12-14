const { instrumentHttpFunction } = require('../open-telemetry/tracing');

const axios = require('axios');

module.exports = instrumentHttpFunction(httpFunction);

async function httpFunction(context, req) {
  context.log('JavaScript HTTP trigger function processed a request.');

  const name = (req.query.name || (req.body && req.body.name));
  const responseMessage = name
    ? "Hello, " + name + ". This HTTP triggered function executed successfully."
    : "This HTTP triggered function executed successfully. Pass a name in the query string or in the request body for a personalized response.";

  await axios.get('http://www.example.com');

  context.res = {
    // status: 200, /* Defaults to 200 */
    body: responseMessage
  };

  const forceStatus = (req.query.forceStatus || (req.body && req.body.forceStatus));
  if (forceStatus) {
    context.res.status = forceStatus;
    console.log(`forcing status to: ${forceStatus}`);
  }

  const throws = (req.query.throws || (req.body && req.body.throws));
  if (throws === "true") {
    throw new Error('Request said to throw');
  }
}
