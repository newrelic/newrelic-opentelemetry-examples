const { instrumentTimerFunction } = require('../open-telemetry/tracing');
const { sendMessage } = require('../service-bus');

const MESSAGES = require('../messages.json');

module.exports = instrumentTimerFunction(queueMessageTimerFunction);

async function queueMessageTimerFunction(context, myTimer) {
  const timeStamp = new Date().toISOString();
  context.log('JavaScript timer trigger function ran!', timeStamp);

  if (myTimer.isPastDue)
  {
    context.log('JavaScript is running late!');
  }

  const message = getNextMessage();
  await sendMessage(message);
};

let _index = 0
function getNextMessage() {
  const messageBody = MESSAGES[_index++];

  if (_index >= MESSAGES.length) {
    _index = 0;
  }

  const message = { body: JSON.stringify(messageBody) };
  return message;
}
