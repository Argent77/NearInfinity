// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.cli;

/**
 * A command-line tool that processes a file.
 */
public interface CommandLineTool {
  /**
   * Runs the tool for the specified file.
   *
   * @param fileName input file name
   * @throws Exception if the tool cannot process the file
   */
  void run(String fileName) throws Exception;
}
