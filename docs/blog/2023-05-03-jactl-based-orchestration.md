---
title:      "Joey: Jactl Orchestration Engine"
date:       2023-05-03 17:06:31 +1000
categories: blog
authors: [james]
---

I am often asked what the point is in having yet another language for the JVM.
Apart from the fun of writing a compiler and being able to run actual programs using my own language, I wanted a
language where the execution state could be captured and persisted such that if the server where the application
is running fails, the execution state can be restored and resumed on another machine.

<!--truncate-->

Of course, we cannot capture the execution state after every instruction as that would be ludicrously expensive.
Instead, we only worry about capturing the execution state when we do something meaningful that changes our state,
such as sending a message to a remote system or changing something in the database.
Until we actually perform some sort of state change, we can recover by taking our previous state and replaying the
last event/message (assuming a reliable messaging protocol that can replay messages/events).

If we don't perform one of these state changing operations then we are a stateless service and there is nothing
that needs to be recovered in the event of a failure.

It so happens that these state changing operations also happen to be operations that can block and are therefore
operations where we would be suspending anyway in order to wait for a result.
One could argue that sending a message (assuming no response is needed) does not need to block, but we can model
this as an asynchronous operation if we want to be able to capture our state at that point.

So, if we use Jactl as our business logic layer then, everytime we suspend, we take our captured state and persist
it in some form so that we can recover it later.
We can use something like Kafka Streams or NATS JetStream as our persistence mechanism.
We generate a unique id to identify each business request that initiates an instance of the orchestration logic
(a Jactl script).
When we persist the state we store the state with the unique id as our key.
The key-value store will always have the latest state for any given id.

Jactl has built-in JSON encoding/decoding support, so it can encode/decode to plain Maps/Lists,
but it also generates encode/decode logic for every user class that is
created and can efficiently encode/decode to and from these class instances as well.
We can use this JSON encoding as our persistence representation for the moment.
We can easily support other, more efficient encoding mechanisms, in the future if needed (e.g. Protocol Buffers).

Given that Jactl is a scripting language to be embedded in reactive/event-loop based applications, we can also
provide a way for application specific state to be persisted alongside the Jactl execution state.
When the Jactl execution state is restored and resumed we would also provide a hook to allow any application
specific state to also be restored at the same time.

Since we are basing everything on a reliable messaging infrastructure such as Kafka or NATS, when something fails
we can take the latest state, recover it, and replay the last event that it received.
This relies on us using Kafka/NATS as the record of all state so that we can transactionally persist the latest
state and send a message or update the database.
Database updates are done by either using Kafka Streams/NATS JetStream as our database or using them as a
reliable transaction log.

When sending and receiving messages we also need to consider timeouts and race conditions between timeouts and the
response message events.
We need to make sure that events for the same business request are processed in order.
The timeouts can be handled as a separate timer service that reliably generates the timeout events, or they can
be the responsibility of the service handling the send/receive message requests (for example).

It is often useful to be able to do things in parallel, rather than do one thing at a time.
If we know that we need results from multiple remote services and there is no order dependency then we would like
to be able to send multiple requests and wait for the results.
We might even want to send multiple requests and wait just for the first result.
This means that we should think about extending the Jactl language to support initiating work in parallel and
waiting for multiple responses.

For example:
```groovy
def fraudResponse
def customerRecord

waitForAll {
  fraudCheck: {
    fraudResponse = sendReceiveJson(...)
  }
  customerLookup: {
    def response = sendReceiveJson(...)
    ...
    customerRecord = response.result
  }
}

// Continue with business logic
...
```

or
```groovy
def fraudResponse
def customerRecord

waitForAny {
  fraudCheck: {
    fraudResponse = sendReceiveJson(...)
  }
  customerLookup: {
    def response = sendReceiveJson(...)
    ...
    customerRecord = response.result
  }
}

// Continue logic
...

```

Despite the illusion that things are happening in parallel, the execution of the Jactl code is still done in a way
to guarantee that the code blocks are only ever executed one at a time and in a thread-safe manner.
