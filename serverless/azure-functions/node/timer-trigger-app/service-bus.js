const opentelemetry = require('@opentelemetry/api');
// There is not currenlty any azure service bus instrumentation in OTel
const { ServiceBusClient } = require('@azure/service-bus');

// name of the queue
const QUEUE_NAME = 'otel-example-queue-items';

async function sendMessage(message, properties) {
  if (!message) {
    return
  }

  return await send(message, properties)
}

async function send(message, properties) {
  if (!process.env.APPLICATION_QUEUE_STORAGE) {
    console.log('Not sending a message, APPLICATION_QUEUE_STORAGE was not set.');
    return;
  }

  const sbClient = new ServiceBusClient(process.env.APPLICATION_QUEUE_STORAGE);
  const sender = sbClient.createSender(QUEUE_NAME);

  try {
    if (!properties) {
      properties = Object.create(null);
      opentelemetry.propagation.inject(opentelemetry.context.active(), properties);
    }

    message.applicationProperties = Object.assign({}, properties);

    await sender.sendMessages(message);

  } catch(error) {
    console.error(error);
  } finally {
    await sender.close();
    await sbClient.close();
  }
}

module.exports = {
  sendMessage
};
