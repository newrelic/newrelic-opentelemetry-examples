const { instrumentHttpFunction } = require('../open-telemetry/tracing');

// Patches function to keep primary function implementation untouched.
// Can directly instrument within function without patching.
module.exports = instrumentHttpFunction(httpFunction);

async function httpFunction(context, req) {
  context.log('JavaScript HTTP trigger function processed a request.');

  const name = (req.query.name || (req.body && req.body.name));
  const responseMessage = name
    ? "Hello, " + name + ". This HTTP triggered function executed successfully."
    : "This HTTP triggered function executed successfully. Pass a name in the query string or in the request body for a personalized response.";

  context.res = {
    // status: 200, /* Defaults to 200 */
    body: responseMessage
  };
}
