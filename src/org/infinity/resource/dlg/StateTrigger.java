// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.nio.ByteBuffer;

public class StateTrigger extends AbstractCode {
  // DLG/StateTrigger-specific field labels
  public static final String DLG_STATE_TRIGGER = "State trigger";

  private int nr;

  public StateTrigger() {
    super(DLG_STATE_TRIGGER);
  }

  public StateTrigger(ByteBuffer buffer, int offset, int count) {
    super(buffer, offset, DLG_STATE_TRIGGER + " " + count);
    this.nr = count;
  }

  public int getNumber() {
    return nr;
  }
}
