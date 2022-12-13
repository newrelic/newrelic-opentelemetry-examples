const { instrumentQueueFunction } = require('../open-telemetry/tracing');

const axios = require('axios');

module.exports = instrumentQueueFunction(webRequestQueueFunction);

async function webRequestQueueFunction (context, myQueueItem) {
  context.log('JavaScript queue trigger function processed work item', myQueueItem);

  if (!process.env.EXTERNAL_URL) {
    console.log('No value set for EXTERNAL_URL, not attempting web request.');
    return;
  }

  try {
    const postBody = JSON.parse(myQueueItem);
    const result = await axios.post(process.env.EXTERNAL_URL, postBody);
  } catch (error) {
    console.log(`Request errored. Handling...`);
    console.error(error.message);
  }
}
