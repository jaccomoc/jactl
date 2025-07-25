package io.jactl.runtime;

public class TimeoutError extends RuntimeError {
  public TimeoutError(String message, String source, int offset) {
    super(message, source, offset);
  }
}
