// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.infinity.datatype.AbstractBitmap;
import org.infinity.datatype.DecNumber;
import org.infinity.datatype.EffectType;
import org.infinity.resource.effects.BaseOpcode;
import org.infinity.resource.itm.ItmResource;
import org.infinity.util.io.StreamUtils;

public final class Effect extends AbstractStruct implements AddRemovable {
  // Effect-specific field labels
  public static final String EFFECT = "Effect";

  private static final int TIMING_MODE_INSTANT_WHILE_EQUIPPED = 2;

  private boolean isNew;

  public Effect() throws Exception {
    super(null, EFFECT, StreamUtils.getByteBuffer(48), 0);
    isNew = true;
    ((DecNumber) getAttribute(BaseOpcode.EFFECT_PROBABILITY_1)).setValue(100);
  }

  public Effect(AbstractStruct superStruct, ByteBuffer buffer, int offset, int number) throws Exception {
    super(superStruct, EFFECT + " " + number, buffer, offset);
  }

  public Effect(AbstractStruct superStruct, ByteBuffer buffer, int offset, String name) throws Exception {
    super(superStruct, name, buffer, offset);
  }

  // --------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove() {
    return true;
  }

  // --------------------- End Interface AddRemovable ---------------------

  @Override
  public void setParent(AbstractStruct parent) {
    if (isNew && parent != null) {
      if (parent instanceof ItmResource) {
        setBitmapValue(EffectType.EFFECT_TYPE_TARGET, EffectType.TARGET_SELF);
        setBitmapValue(BaseOpcode.EFFECT_TIMING_MODE, TIMING_MODE_INSTANT_WHILE_EQUIPPED);
      }
      isNew = false;
    }
    super.setParent(parent);
  }

  @Override
  public int read(ByteBuffer buffer, int offset) throws Exception {
    EffectType type = new EffectType(buffer, offset, 2);
    addField(type);
    final List<StructEntry> list = new ArrayList<>();
    offset = type.readAttributes(buffer, offset + 2, list);
    addFields(getFields().size() - 1, list);
    return offset;
  }

  /**
   * Creates a copy of the current structure, optionally converted to the EFF V2.0 format.
   *
   * @param asV2 {@code true} if result should be of {@link Effect2} type.
   * @return A copy of the current instance.
   * @throws Exception
   */
  public Object clone(boolean asV2) throws Exception {
    final AbstractStruct retVal;

    if (asV2) {
      ByteBuffer src = getDataBuffer().order(ByteOrder.LITTLE_ENDIAN);
      ByteBuffer dst = StreamUtils.getByteBuffer(264);
      byte[] resref = new byte[8];

      dst.putInt(0); // Signature
      dst.putInt(0); // Version
      dst.putInt(src.getShort(0x00)); // Type
      dst.putInt(src.get(0x02)); // Target
      dst.putInt(src.get(0x03)); // Power
      dst.putInt(src.getInt(0x04)); // Parameter 1
      dst.putInt(src.getInt(0x08)); // Parameter 2
      dst.putInt(src.get(0x0c)); // Timing mode
      dst.putInt(src.getInt(0x0e)); // Duration
      dst.putShort(src.get(0x12)); // Probability 1
      dst.putShort(src.get(0x13)); // Probability 2
      src.position(0x14);
      src.get(resref);
      dst.put(resref); // Resource
      src.position(0);
      dst.putInt(src.getInt(0x1c)); // # dice thrown
      dst.putInt(src.getInt(0x20)); // Dice size
      dst.putInt(src.getInt(0x24)); // Save type
      dst.putInt(src.getInt(0x28)); // Save bonus
      dst.putInt(src.getInt(0x2c)); // Special
      dst.putInt(0); // Primary type (shool)
      dst.putInt(0); // Unused
      dst.putInt(0); // Minimum level
      dst.putInt(0); // Maximum level
      dst.putInt(src.get(0x0d)); // Dispel/Resistance
      dst.putInt(0); // Parameter 3
      dst.putInt(0); // Parameter 4
      dst.putInt(0); // Parameter 5 (unused)
      dst.putInt(0); // Time applied (ticks)
      dst.putInt(0).putInt(0); // Resource 2
      dst.putInt(0).putInt(0); // Resource 3
      dst.putInt(-1); // Caster location: X
      dst.putInt(-1); // Caster location: Y
      dst.putInt(-1); // Target location: X
      dst.putInt(-1); // Target location: Y
      dst.putInt(0); // Resource type
      dst.putInt(0).putInt(0); // Parent resource
      dst.putInt(0); // Resource flags
      dst.putInt(0); // Impact projectile
      dst.putInt(-1); // Source item slot
      dst.position(dst.position() + 32); // Variable name
      dst.putInt(0); // Caster level
      dst.putInt(0); // Internal flags
      dst.putInt(0); // Secondary type
      dst.position(0);

      int offset = getOffset();
      retVal = new Effect2(null, dst, 0, getName());
      retVal.setOffset(offset);
      retVal.realignStructOffsets();
    } else {
      retVal = clone();
    }

    return retVal;
  }

  private void setBitmapValue(String attributeName, int value) {
    final StructEntry entry = getAttribute(attributeName);
    if (!(entry instanceof AbstractBitmap<?>)) {
      throw new IllegalStateException("Effect attribute is not a bitmap: " + attributeName);
    }

    final AbstractBitmap<?> bitmap = (AbstractBitmap<?>) entry;
    final ByteBuffer buffer = StreamUtils.getByteBuffer(bitmap.getSize());
    switch (bitmap.getSize()) {
      case 1:
        buffer.put((byte) value);
        break;
      case 2:
        buffer.putShort((short) value);
        break;
      case 4:
        buffer.putInt(value);
        break;
      default:
        throw new IllegalStateException("Unsupported bitmap size: " + bitmap.getSize());
    }
    bitmap.read(buffer, 0);
  }
}
