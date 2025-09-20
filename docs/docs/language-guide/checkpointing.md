---
title: Checkpointing
---

Jactl provides a `checkpoint()` function that allows scripts to save their current state.
Checkpointing can be used to provide a way to restore scripts from their last checkpoint after an outage.
They also provide a way for an application to have scripts that need to suspend themselves for a long period of time
and resume after a long timeout or when some event occurs sometime in the future.

For example, if a script needs to send an email and wait for a response that may be days or weeks later, then the
script can checkpoint its state and be resumed once the email is received or a timeout occurs.
Of course, the script could just sit around waiting, rather than suspending itself, but this takes up memory and 
relies on the process in which it is running to never fail.

The `checkpoint()` function takes two arguments:
1. a `commit` closure that is invoked before the checkpoint, and
2. a `recover` closure that is invoked when the script is resumed.

The `checkpoint()` functions returns the value returned by whichever closure runs as its return value.
By returning a different value from each closure, the script knows whether it is running in the "sunny day" 
state and the commit has just occurred, or whether it has been resumed and the recover has just occurred
and can use this information if needed for its later processing.

For example:
```groovy
def recovered =
  checkpoint(
    commit : {
      sendResponseMsg(replyTo, messageId, [possibleRepeat:false])
      false   // sunny day
    },
    recover: {
      sendResponseMsg(replyTo, messageId, [possibleRepeat:true])
      true    // indicate we have been recovered
    }
  )

if (recovered) {
  ...
}
else {
  ...
}
```

If checkpointing is required, it is up to the application in which Jactl is embedded to provide a
mechanism for persisting the checkpoints and to also provide a way to read the checkpoints in the event
of a failure and invoke `Restorer.restore()` to create a `Continuation` from the checkpoint and
then resume the `Continuation` to allow the script to continue from where the checkpoint occurred.

See the [Integration Guide](../integration-guide/introduction) for more details.
