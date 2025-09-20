---
title: Checkpoints
---

Jactl provides a `checkpoint()` function that allows scripts to checkpoint their current state with aim of then being
able to restore this state elsewhere and continue execution from where the script left off in the event of an outage.
Jactl provides the checkpoint state as a `byte[]` object, but it is up to the execution environment in which Jactl is
embedded to provide a way to preserve this state and to then decide when it is appropriate to continue the execution
state of the checkpoint on another instance (or after a restart on the same instance, for example).

The checkpoint state could be stored to a database, or replicated over a network, or written to the file system.
It is up to the application to decide what is appropriate based on the overall architecture of the solution.
If the scripts never invoke `checkpoint()` or there is no need to replicate or preserve script state then this feature
can be ignored.

The `JactlEnv` implementation discussed above has two additional methods for dealing with checkpoints:
1. `saveCheckpoint()`, and
2. `deleteCheckpoint()`

If an application wants to be able to save checkpoint state then the application should provide an implementation for
both of these methods, however, since these methods have `default` implementations that do nothing, it is not mandatory
to provide implementations for these methods.

## saveCheckpoint()

The signature for this method is:
```java
  void saveCheckpoint(UUID             id,
                      int              checkpointId,
                      byte[]           checkpoint,
                      String           source,
                      int              offset,
                      Object           result,
                      Consumer<Object> resumer);
```

The parameters are:

| Parameter    | Description                                                                                                                                          |
|:-------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------|
| id           | UUID that uniquely identifies the running script instance.                                                                                           |
| checkpointId | If the script checkpoints multiple times this identifies which checkpoint we are up to. It is guaranteed to be an incrementing number with no gaps.  |
| checkpoint   | The actual checkpointed state of the script instance as a `byte[]` object.                                                                           |
| source       | The location in the source code where `checkpoint()` is called (for error reporting).                                                                |
| offset       | Offset into source where `checkpoint()` is called (for error reporting).                                                                             |
| result       | The result that should be passed to the `resumer` once the checkpoint has been saved.                                                                |
| resumer      | A callback supplied by the Jactl runtime that needs to be invoked to resume execution of the script instance once the checkpoint has been saved.     |

All script executions are given a unique UUID to identify the executing instance regardless of what actual script is
being executed.
Over the lifetime of the application many script instances will be executed for many scripts and the UUID identifies
each of these instances.

When a script instance invokes the `checkpoint()` function to checkpoint its state it keeps track of how many times it
has been checkpointed before and the `checkpointId` parameter is an incrementing count that allows us to identify the
checkpoint for that particular script instance.
The combination of the `id` and the `checkpointId` uniquely identifies a given checkpoint state.

The call to saveCheckpoint() is invoked from an event-loop thread so implementations need to be careful
not to block this thread while saving the checkpoint.

Once the checkpoint has been saved, the `resumer` object should be invoked, passing in the `result` or a `RuntimeError`
object if an error has occurred.
It is up to the implementation to make sure that this is done on an event-loop (non-blocking scheduler) thread.

A naive, incomplete, implementation of `saveCheckpoint()` that just appends each checkpoint to a file in `/tmp`
could look like this:
```java
  static JactlEnv env;
    
  @Override
  public void saveCheckpoint(UUID             id,
                             int              checkpointId,
                             byte[]           checkpoint,
                             String           source,
                             int              offset,
                             Object           result,
                             Consumer<Object> resumer) {
    // If environement supports scheduling on a specific thread then remember current thread
    Object threadContext = env.getThreadContext();
    
    env.scheduleBlocking(() => {
      Object retVal = result;
      try {
        FileOutputStream fileOutput = new FileOutputStream("/tmp/checkpoints.data", true);
        fileOutput.write(checkpoint);
        fileOutput.close();
      }
      catch (IOException e) {
        retVal = new RuntimeError("Error persisting checkpoint", source, offset, e);
      }
      Object finalRetVal = retVal;
      
      // Make sure resumption of script is done on event-loop thread (pass in threadContext if environment
      // supports scheduling onto a specific thread)
      env.scheduleEvent(threadContext, () => resumer.accept(finalRetVal));
    });
  }
```

:::note
Once a checkpoint has been saved it is safe to delete the previous checkpoint for that script instance.
Alternatively, you can wait for the final `deleteCheckpoint()` call to know when to clean up the old
checkpoints.
:::

## deleteCheckpoint()

In order to know when it is safe to clean up old checkpoints, at the end of a script instance execution the
`deleteCheckpoint()` is invoked, passing in the id of the script instance and the last checkpoint id that was
saved for that instance.

The `deleteCheckpoint()` call is done on an event-loop thread and, since there is no need to actually guarantee that
the deletion has completed before returning, if your implementation does anything that will block then you should make
sure to schedule the work on a separate blocking thread.

