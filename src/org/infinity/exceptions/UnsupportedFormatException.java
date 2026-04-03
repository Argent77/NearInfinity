// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.exceptions;

/**
 * Thrown if a game resource format is not supported by Near Infinity.
 */
public class UnsupportedFormatException extends Exception {
  /** Constructs an {@code UnsupportedFormatException} with no detail message. */
  public UnsupportedFormatException() {
    super();
  }

  /**
   * Constructs an {@code UnsupportedFormatException} with the specified detail message.
   *
   * @param message the detail message.
   */
  public UnsupportedFormatException(String message) {
    super(message);
  }

  /**
   * Constructs an {@code UnsupportedFormatException} with the specified detail message and cause.
   *
   * @param message the detail message.
   * @param cause   the cause for this exception to be thrown.
   */
  public UnsupportedFormatException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs an {@code UnsupportedFormatException} with a cause but no detail message.
   *
   * @param cause the cause for this exception to be thrown.
   */
  public UnsupportedFormatException(Throwable cause) {
    super(cause);
  }
}
