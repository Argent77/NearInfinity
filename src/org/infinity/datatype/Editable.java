// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2019 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.event.ActionListener;

import javax.swing.JComponent;

import org.infinity.gui.StructViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.StructEntry;

/**
 * Interface for fields that editable via external dialog, and not via inline editor
 * in the {@link StructViewer}. For inline editing {@link Datatype datatype} must
 * implement the {@link InlineEditable} interface.
 */
public interface Editable extends StructEntry
{
  /**
   * Used to create and setup editor for this object.
   *
   * @param container
   * @return Component that will be used to edit this object. Must not be {@code null}
   */
  JComponent edit(ActionListener container);

  /** Called after {@link #edit} and after created editor is showed. */
  void select();

  /**
   * Updates properties of the specified structure from this object. This method
   * called after {@link #edit} and {@link #select}.
   *
   * @param struct Structure that owns that object and must be updated
   * @return {@code true} if object succesfully changed, {@code false} otherwise
   */
  boolean updateValue(AbstractStruct struct);
}
