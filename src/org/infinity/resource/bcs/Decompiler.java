// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.bcs;

import java.awt.Point;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.CreMapCache;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.StringBufferStream;
import org.infinity.util.StringTable;
import org.infinity.util.io.StreamUtils;

public final class Decompiler
{
  private final Set<Integer> strrefsUsed = new HashSet<>();
  private final Set<ResourceEntry> resourcesUsed = new HashSet<>();
  private final SortedMap<Integer, String> idsErrors = new TreeMap<>();
  private String code;    // byte code
  private String source;  // decompiled sources
  private ScriptType scriptType;
  private String indent = "\t";
  private boolean generateErrors, generateComments, generateResUsed;
  private int lineNr;
  private Signatures triggers, actions;
  private boolean triggerOverrideEnabled;

  public Decompiler(String code, boolean generateErrors)
  {
    this(code, ScriptType.BCS, generateErrors);
  }

  public Decompiler(String code, ScriptType type, boolean generateErrors)
  {
    if (BrowserMenuBar.getInstance() != null) {
      if (BrowserMenuBar.getInstance().getBcsAutoIndentEnabled()) {
        indent = BrowserMenuBar.getInstance().getBcsIndent();
      } else {
        indent = "";
      }
    }
    this.scriptType = type;
    this.generateErrors = generateErrors;
    this.generateComments = true;
    this.generateResUsed = true;
    this.code = (code != null) ? code : "";
    setTriggerOverrideEnabled(this.scriptType != ScriptType.TRIGGER);
  }

  /** Returns the unprocessed BCS byte code. */
  public String getCode()
  {
    return code;
  }

  /** Sets new BCS byte code to compile. */
  public void setCode(String code)
  {
    this.code = (code != null) ? code : "";
    reset();
  }

  public void setCode(ResourceEntry bcs) throws Exception
  {
    if (bcs == null) {
      throw new NullPointerException();
    }
    ByteBuffer buffer = bcs.getResourceBuffer();
    this.code = StreamUtils.readString(buffer, buffer.limit());
    reset();
  }

  /** Returns the decompiled script source. Executes {@code decompile()} if needed. */
  public String getSource() throws Exception
  {
    if (source == null) {
      decompile();
    }
    return source;
  }

  /** Returns currently used script type. */
  public ScriptType getScriptType()
  {
    return scriptType;
  }

  /**
   * Specifies new script type.
   * <b>Note:</b> Automatically invalidates previously decompiled script code.
   * @param type Type of Script
   */
  public void setScriptType(ScriptType type)
  {
    if (type != scriptType) {
      reset();
      scriptType = type;
      setTriggerOverrideEnabled(scriptType != ScriptType.TRIGGER);
    }
  }

  /** Returns whether to generate compiler errors. */
  public boolean isGenerateErrors()
  {
    return generateErrors;
  }

  /**
   * Specify whether to generate decompile errors.
   * <b>Note:</b> Automatically invalidates previously decompiled script code.
   */
  public void setGenerateErrors(boolean enable)
  {
    if (enable != generateErrors) {
      reset();
      generateErrors = enable;
    }
  }

  /** Returns whether function-specific comments are generated. */
  public boolean isGenerateComments()
  {
    return generateComments;
  }

  /** Specify whether function-specific comments should be generated. */
  public void setGenerateComments(boolean enable)
  {
    if (enable != generateComments) {
      reset();
      generateComments = enable;
    }
  }

  /** Returns currently used string for a single level of indentation. */
  public String getIndent()
  {
    return indent;
  }

  /** Applies the indentation string defined in the currently selected item in the Options menu. */
  public void setIndent()
  {
    if (BrowserMenuBar.getInstance() != null) {
      if (BrowserMenuBar.getInstance().getBcsAutoIndentEnabled()) {
        indent = BrowserMenuBar.getInstance().getBcsIndent();
      } else {
        indent = "";
      }
    }
  }

  /** Applies the specified string for a single level of indentation. */
  public void setIndent(String newIndent)
  {
    if (newIndent != null && !indent.equals(newIndent)) {
      indent = newIndent;
    }
  }

  /**
   * Returns whether the artificial construct TriggerOverride() will be used.
   * <b>Note:</b> This flag is automatically updated whenever a new script type is set.
   */
  public boolean isTriggerOverrideEnabled()
  {
    return triggerOverrideEnabled;
  }

  /**
   * Sets whether the artificial construct TriggerOverride() will be used.
   * <b>Note:</b> This flag is automatically updated whenever a new script type is set.
   */
  public void setTriggerOverrideEnabled(boolean set)
  {
    triggerOverrideEnabled = set;
  }

  public SortedMap<Integer, String> getIdsErrors()
  {
    return idsErrors;
  }

  /** Returns whether a used resources map is generated. */
  public boolean isGenerateResourcesUsed()
  {
    return generateResUsed;
  }

  /** Specify whether a used resources map should be generated. */
  public void setGenerateResourcesUsed(boolean enable)
  {
    if (enable != generateResUsed) {
      reset();
      generateResUsed = enable;
    }
  }

  public Set<ResourceEntry> getResourcesUsed()
  {
    return resourcesUsed;
  }

  public Set<Integer> getStringRefsUsed()
  {
    return strrefsUsed;
  }

  /**
   * Decompiles the currently loaded script code into human-readable source.
   * Uses {@link #getScriptType()} to determine the correct decompile action.
   * @return The decompiled script source.
   * @throws Exception Thrown if script type is {@code Custom}.
   */
  public String decompile() throws Exception
  {
    return decompile(scriptType);
  }

  /**
   * Decompiles the currently loaded script code into human-readable source.
   * @param type Specifies the decompile action.
   * @return The decompiled script source.
   * @throws Exception Thrown if script type is {@code Custom}.
   */
  public String decompile(ScriptType type) throws Exception
  {
    if (type != getScriptType()) {
      reset();
    }

    switch (type) {
      case BCS:     return decompileScript();
      case TRIGGER: return decompileTriggers();
      case ACTION:  return decompileActions();
      default:      throw new IllegalArgumentException("Could not determine script type");
    }
  }

  public String decompileScript() throws Exception
  {
    reset();
    init();
    StringBuilder sb = new StringBuilder(code.length() * 2);
    StringBufferStream sbs = new StringBufferStream(code);
    sbs.setAutoSkipWhitespace(true);
    if (sbs.skip("SC")) {
      while (!sbs.eos() && !sbs.skip("SC")) {
        if (sbs.skip("CR")) {
          decompileCR(sb, sbs);
        } else {
          sbs.skip();
        }
      }
    }
    source = sb.toString();
    return source;
  }

  public String decompileTriggers() throws Exception
  {
    // Note: majority of code is identical with decompileCO()
    String curIndent = indent;
    try {
      reset();
      init();
      StringBuilder sb = new StringBuilder(code.length() * 2);
      StringBufferStream sbs = new StringBufferStream(code);
      sbs.setAutoSkipWhitespace(true);
      long orCount = 0;
      BcsTrigger override = null;
      while (!sbs.eos()) {
        if (sbs.skip("TR")) {
          // decoding next trigger
          BcsTrigger trigger = new BcsTrigger(sbs, triggers);
          if (isTriggerOverrideEnabled() &&
              override == null && trigger.isOverride()) {
            override = trigger;   // save for later
          } else {
            if (override != null) {
              // prepare for combining trigger with previous NextTriggerObject() into TriggerOverride()
              trigger.setOverride(override);
              override = null;
            }
            if (orCount > 0) {
              sb.append(indent);
              if (!trigger.isOverride()) {
                orCount--;
              }
            } else if (trigger.isOR()) {
              orCount = trigger.getORCount();
            }
            sb.append(decompileTrigger(trigger)).append('\n');
            lineNr++;
          }
        } else {
          sbs.skip();
        }
      }

      // recovering pending override trigger
      if (override != null) {
        if (orCount > 0) {
          sb.append(indent);
          orCount--;
        }
        sb.append(decompileTrigger(override)).append('\n');
        override = null;
      }

      source = sb.toString();
    } finally {
      indent = curIndent;
    }
    return source;
  }

  public String decompileActions() throws Exception
  {
    String curIndent = indent;
    try {
      reset();
      init();
      StringBuilder sb = new StringBuilder(code.length() * 2);
      StringBufferStream sbs = new StringBufferStream(code);
      sbs.setAutoSkipWhitespace(true);
      while (!sbs.eos()) {
        if (sbs.skip("AC")) {
          BcsAction action = new BcsAction(sbs, actions);
          sb.append(decompileAction(action)).append('\n');
          lineNr++;
        } else {
          sbs.skip();
        }
      }
      source = sb.toString();
    } finally {
      indent = curIndent;
    }
    return source;
  }

  private void reset()
  {
    strrefsUsed.clear();
    resourcesUsed.clear();
    idsErrors.clear();
    lineNr = 1;
    source = null;
  }

  private void init()
  {
    triggers = Signatures.getTriggers();
    actions = Signatures.getActions();
  }


  private void decompileCR(StringBuilder sb, StringBufferStream sbs) throws Exception
  {
    while (!sbs.eos() && !sbs.skip("CR")) {
      if (sbs.skip("CO")) {
        decompileCO(sb, sbs);
      } else if (sbs.skip("RS")) {
        decompileRS(sb, sbs);
      } else {
        sbs.skip();
      }
    }
    sb.append("END\n\n");
    lineNr += 2;
  }

  private void decompileCO(StringBuilder sb, StringBufferStream sbs) throws Exception
  {
    // Note: majority of code is identical with decompileTriggers()
    sb.append("IF\n");
    lineNr++;
    long orCount = 0;
    BcsTrigger override = null;
    while (!sbs.eos() && !sbs.skip("CO")) {
      if (sbs.skip("TR")) {
        // decoding next trigger
        BcsTrigger trigger = new BcsTrigger(sbs, triggers);
        if (isTriggerOverrideEnabled() &&
            override == null && trigger.isOverride()) {
          override = trigger;   // save for later
        } else {
          if (override != null) {
            // prepare for combining trigger with previous NextTriggerObject() into TriggerOverride()
            trigger.setOverride(override);
            override = null;
          }
          if (orCount > 0) {
            sb.append(indent);
            if (!trigger.isOverride()) {
              orCount--;
            }
          } else if (trigger.isOR()) {
            orCount = trigger.getORCount();
          }
          sb.append(indent).append(decompileTrigger(trigger)).append('\n');
          lineNr++;
        }
      } else {
        sbs.skip();
      }
    }

    // recovering pending override trigger
    if (override != null) {
      if (orCount > 0) {
        sb.append(indent);
        orCount--;
      }
      sb.append(indent).append(decompileTrigger(override)).append('\n');
      override = null;
    }
  }

  private void decompileRS(StringBuilder sb, StringBufferStream sbs) throws Exception
  {
    sb.append("THEN\n");
    lineNr++;
    while (!sbs.eos() && !sbs.skip("RS")) {
      if (sbs.skip("RE")) {
        decompileRE(sb, sbs);
      } else {
        sbs.skip();
      }
    }
  }

  private void decompileRE(StringBuilder sb, StringBufferStream sbs) throws Exception
  {
    String weight = sbs.getMatch("[0-9]+");
    if (weight != null) {
      try {
        int i = Integer.parseInt(weight);
        sb.append(indent).append("RESPONSE #").append(i).append('\n');
        lineNr++;
      } catch (NumberFormatException e) {
        throw new Exception("Invalid response weight: " + weight);
      }
    } else {
      throw new Exception("Missing or invalid response weight");
    }
    while (!sbs.eos() && !sbs.skip("RE")) {
      if (sbs.skip("AC")) {
        BcsAction action = new BcsAction(sbs, actions);
        sb.append(indent).append(indent).append(decompileAction(action)).append('\n');
        lineNr++;
      } else {
        sbs.skip();
      }
    }
  }

  private String decompileTrigger(BcsTrigger trigger) throws Exception
  {
    StringBuilder sb = new StringBuilder();

    Signatures.Function[] functions = trigger.signatures.getFunction(trigger.id);
    if (functions == null) {
      trigger.id ^= 0x4000;
      functions = trigger.signatures.getFunction(trigger.id);
      if (functions == null) {
        trigger.id ^= 0x4000;
      }
    }
    if (functions == null) {
      if (isGenerateErrors()) {
        idsErrors.put(Integer.valueOf(lineNr),
                      String.format("0x%04X not found in %s",
                          trigger.id, trigger.signatures.getResource().toUpperCase(Locale.ENGLISH)));
      }
      return String.format("// Error - Could not find trigger 0x%04X", trigger.id);
    }

    Signatures.Function function = trigger.getMatchingFunction();
    if (function == null) {
      if (isGenerateErrors()) {
        idsErrors.put(Integer.valueOf(lineNr),
                      String.format("No matching signature found for 0x%04X in %s",
                          trigger.id, trigger.signatures.getResource().toUpperCase(Locale.ENGLISH)));
      }
      return String.format("// Error - Could not find matching signature for trigger 0x%04X", trigger.id);
    }

    if (trigger.negated) {
      sb.append('!');
    }

    // handling TriggerOverride()
    BcsTrigger override = trigger.getOverride();
    if (override != null) {
      // TBC: Can NextTriggerObject() be negated?
      String obj = decompileObject(override.t6);
      sb.append(Signatures.Function.TRIGGER_OVERRIDE_NAME).append('(').append(obj).append(',');
    }

    // dealing with actual trigger
    sb.append(function.getName()).append('(');

    String comment = null;
    int curNum = 0, curString = 0, curObj = 0, curPoint = 0;
    for (int i = 0, cnt = function.getNumParameters(); i < cnt; i++) {
      if (i > 0) {
        sb.append(',');
      }

      Signatures.Function.Parameter p = function.getParameter(i);
      switch (p.getType()) {
        case Signatures.Function.Parameter.TYPE_INTEGER:
        {
          long value;
          try {
            value = trigger.getNumericParam(curNum);
          } catch (IllegalArgumentException e) {
            value = 0;
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for number at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompileNumber(value, p);
          sb.append(s);
          String c = generateNumberComment(value, p, ScriptInfo.getInfo().isCommentAllowed(function.getId(), i));
          if (comment == null && !c.isEmpty()) {
            comment = c;
          }
          curNum++;
          break;
        }
        case Signatures.Function.Parameter.TYPE_STRING:
        {
          String value;
          try {
            value = trigger.getStringParam(function, curString);
          } catch (IllegalArgumentException e) {
            value = "";
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for string at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompileString(value, p);
          sb.append(s);
          String c = generateStringComment(value, p, ScriptInfo.getInfo().isCommentAllowed(function.getId(), i));
          if (comment == null && !c.isEmpty()) {
            comment = c;
          }
          curString++;
          break;
        }
        case Signatures.Function.Parameter.TYPE_OBJECT:
        {
          BcsObject value;
          try {
            value = trigger.getObjectParam(curObj);
          } catch (IllegalArgumentException e) {
            value = BcsObject.getEmptyObject();
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for object at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompileObject(value);  // defaults to "[ANYONE]"
          sb.append(s);
          String c = generateObjectComment(value, ScriptInfo.getInfo().isCommentAllowed(function.getId(), i));
          if (comment == null && !c.isEmpty()) {
            comment = c;
          }
          curObj++;
          break;
        }
        case Signatures.Function.Parameter.TYPE_POINT:
        {
          Point value;
          try {
            value = trigger.getPointParam(curPoint);
          } catch (IllegalArgumentException e) {
            value = new Point();
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for point at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompilePoint(value);
          sb.append(s);
          curPoint++;
          break;
        }
        default:
          if (isGenerateErrors()) {
            idsErrors.put(Integer.valueOf(lineNr), "Unknown type for parameter " + i + ".");
          }
          return String.format("// Error - %s: Unknown type for parameter %d", function.getName(), i);
      }
    }

    sb.append(')');

    if (override != null) {
      sb.append(')');
    }

    if (comment != null) {
      sb.append("  // ").append(comment.replace('\n', ' '));
    }

    return sb.toString();
  }

  private String decompileAction(BcsAction action) throws Exception
  {
    StringBuilder sb = new StringBuilder();

    Signatures.Function[] functions = action.signatures.getFunction(action.id);
    if (functions == null) {
      if (isGenerateErrors()) {
        idsErrors.put(Integer.valueOf(lineNr),
                      String.format("%d not found in %s",
                          action.id, action.signatures.getResource().toUpperCase(Locale.ENGLISH)));
      }
      return String.format("// Error - Could not find action %d", action.id);
    }

    Signatures.Function function = action.getMatchingFunction();
    if (function == null) {
      if (isGenerateErrors()) {
        idsErrors.put(Integer.valueOf(lineNr),
                      String.format("No matching signature found for %d in %s",
                          action.id, action.signatures.getResource().toUpperCase(Locale.ENGLISH)));
      }
      return String.format("// Error - Could not find matching signature for action %d", action.id);
    }

    BcsObject override = action.getObjectParam(0);
    String comment = null;
    int curNum = 0, curObj = 1, curString = 0, curPoint = 0;  // curObj: skipping ActionOverride

    // constructing action
    sb.append(function.getName()).append('(');

    for (int i = 0, cnt = function.getNumParameters(); i < cnt; i++) {
      if (i > 0) {
        sb.append(',');
      }

      Signatures.Function.Parameter param = function.getParameter(i);
      switch (param.getType()) {
        case Signatures.Function.Parameter.TYPE_INTEGER:
        {
          long value;
          try {
            value = action.getNumericParam(curNum);
          } catch (IllegalArgumentException e) {
            value = 0;
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for number at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompileNumber(value, param);
          sb.append(s);
          String c = generateNumberComment(value, param, ScriptInfo.getInfo().isCommentAllowed(function.getId(), i));
          if (comment == null && !c.isEmpty()) {
            comment = c;
          }
          curNum++;
          break;
        }
        case Signatures.Function.Parameter.TYPE_STRING:
        {
          String value;
          try {
            value = action.getStringParam(function, curString);
          } catch (IllegalArgumentException e) {
            value = "";
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for string at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompileString(value, param);
          sb.append(s);
          String c = generateStringComment(value, param, ScriptInfo.getInfo().isCommentAllowed(function.getId(), i));
          if (comment == null && !c.isEmpty()) {
            comment = c;
          }
          curString++;
          break;
        }
        case Signatures.Function.Parameter.TYPE_POINT:
        {
          Point value;
          try {
            value = action.getPointParam(curPoint);
          } catch (IllegalArgumentException e) {
            value = new Point();
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for point at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompilePoint(value);
          sb.append(s);
          curPoint++;
          break;
        }
        case Signatures.Function.Parameter.TYPE_OBJECT:
        {
          BcsObject value = null;
          try {
            value = action.getObjectParam(curObj);
          } catch (IllegalArgumentException e) {
            value = BcsObject.getEmptyObject();
            if (isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), "No value defined for object at parameter " + i + ". Using defaults.");
            }
          }
          String s = decompileObject(value);  // defaults to "[ANYONE]"
          sb.append(s);
          String c = generateObjectComment(value, ScriptInfo.getInfo().isCommentAllowed(function.getId(), i));
          if (comment == null && !c.isEmpty()) {
            comment = c;
          }
          curObj++;
          break;
        }
        case Signatures.Function.Parameter.TYPE_ACTION:
          // ignore
          break;
      }
    }

    sb.append(')');

    // handling ActionOverride()
    if (!override.isEmpty()) {
      String funcName = null;
      functions = action.signatures.getFunction(1);
      for (Signatures.Function f: functions) {
        if (f.getNumParameters() == 2 &&
            f.getParameter(0).getType() == Signatures.Function.Parameter.TYPE_OBJECT &&
            f.getParameter(1).getType() == Signatures.Function.Parameter.TYPE_ACTION) {
          funcName = f.getName();
          break;
        }
      }
      if (funcName == null) {
        funcName = Signatures.Function.ACTION_OVERRIDE_NAME;
      }
      StringBuilder sbOverride = new StringBuilder();
      sbOverride.append(funcName).append('(');
      sbOverride.append(decompileObject(override)).append(',');
      sb.insert(0, sbOverride.toString());
      sb.append(')');
    }

    if (comment != null) {
      sb.append("  // ").append(comment.replace('\n', ' '));
    }

    return sb.toString();
  }

  // Returns fully qualified object specifier.
  private String decompileObject(BcsObject object)
  {
    StringBuilder sb = new StringBuilder();

    if (object == null) {
      sb.append(decompileObjectTarget(null, true));
    } else {
      String target = null;
      String rect = null;

      // getting target object
      target = decompileObjectTarget(object, false);

      // getting string
      if (target == null && !object.isEmptyString()) {
        target = '"' + object.name + '"';
      }

      // getting identifier list (ordered from most outer to most inner identifier)
      List<String> listIdentifiers = null;
      if (!object.isEmptyIdentifier()) {
        listIdentifiers = new ArrayList<>();
        IdsMap map = IdsMapCache.get("OBJECT.IDS");
        if (map == null && isGenerateErrors()) {
          idsErrors.put(Integer.valueOf(lineNr), "Could not retrieve values from OBJECT.IDS");
        }
        boolean found = false;
        for (int i = object.identifier.length - 1; i >= 0; i--) {
          if (object.identifier[i] != 0) {
            found = true;
            IdsMapEntry entry = null;
            if (map != null) {
              entry = map.get(object.identifier[i]);
              if (entry != null) {
                listIdentifiers.add(getNormalizedSymbol(entry.getSymbol()));
              }
            }
            if (map == null || entry == null) {
              listIdentifiers.add("UnknownObject" + object.identifier[i]);
            }
          } else if (found) {
            break;
          }
        }
      }

      if (target == null && listIdentifiers == null) {
        // using default target
        target = decompileObjectTarget(null, true);
      }

      // optional: getting region
      if (!object.isEmptyRect()) {
        StringBuilder sbRect = new StringBuilder();
        sbRect.append('[');
        sbRect.append(object.region.x).append('.');
        sbRect.append(object.region.y).append('.');
        sbRect.append(object.region.width).append('.');
        sbRect.append(object.region.height);
        sbRect.append(']');
        rect = sbRect.toString();
      }

      // assembling object string
      StringBuilder sbClosing = null;
      if (listIdentifiers != null) {
        sbClosing = new StringBuilder();
        for (int i = 0, cnt = listIdentifiers.size(); i < cnt; i++) {
          sb.append(listIdentifiers.get(i));
          if (i + 1 < cnt || target != null) {
            sb.append('(');
            sbClosing.append(')');
          }
        }
      }

      if (target != null) {
        sb.append(target);
      }

      if (sbClosing != null) {
        sb.append(sbClosing.toString());
      }

      if (rect != null) {
        sb.append(rect);
      }
    }

    return sb.toString();
  }

  // Decompiles a target ([EA.GENERAL.RACE...]). Returns "[ANYONE]" if useDefault is true, null otherwise.
  private String decompileObjectTarget(BcsObject object, boolean useDefault)
  {
    String retVal = null;

    if (object != null && !object.isEmptyTarget()) {
      StringBuilder sb = new StringBuilder();
      long[] idsValues = new long[object.target.length];
      System.arraycopy(object.target, 0, idsValues, 0, idsValues.length);
      int numTargetValues = 0;
      for (int i = idsValues.length - 1; i >= 0; i--) {
        if (idsValues[i] != 0) {
          numTargetValues = i + 1;
          break;
        }
      }
      if (numTargetValues > 0) {
        ScriptInfo info = ScriptInfo.getInfo();
        String[] idsNames = BcsObject.getTargetList();
        boolean isIwd2 = (Profile.getEngine() == Profile.Engine.IWD2);
        long race = 0; // store RACE value in IWD2
        sb.append('[');
        for (int i = 0; i < numTargetValues; i++) {
          if (i > 0) {
            sb.append('.');
          }

          String symbol = null;
          if (isIwd2) {
            // IWD2 needs RACE value to correctly look up SUBRACE symbol
            if (i == info.IDX_OBJECT_RACE) {
              race = idsValues[i];
            } else if (i == info.IDX_OBJECT_SUBRACE) {
              idsValues[i] |= race << 16;
            }
          }
          if (idsValues[i] != 0) {
            // don't use symbols for 0
            IdsMap map = IdsMapCache.get(idsNames[i] + ".IDS");
            if (map != null) {
              IdsMapEntry entry = map.get(idsValues[i]);
              if (entry != null) {
                symbol = getNormalizedSymbol(entry.getSymbol());
              }
            }
            if (symbol == null && isGenerateErrors()) {
              idsErrors.put(Integer.valueOf(lineNr), idsValues[i] + " not found in " + idsNames[i] + ".IDS");
            }
          }

          if (symbol == null) {
            if (isIwd2 && i == info.IDX_OBJECT_SUBRACE) {
              // reverting RACE + SUBRACE combination
              idsValues[i] &= ~(race << 16);
            }
            symbol = Long.toString(idsValues[i]);
          }

          sb.append(symbol);
        }
        sb.append(']');
        retVal = sb.toString();
      }
    }

    if (useDefault && retVal == null) {
      retVal = "[ANYONE]";
    }

    return retVal;
  }

  // Returns point structure formatted as [x.y]
  private String decompilePoint(Point value)
  {
    StringBuilder sb = new StringBuilder();

    if (value != null) {
      sb.append('[').append(value.x).append('.').append(value.y).append(']');
    } else {
      sb.append("[0.0]");
    }

    return sb.toString();
  }

  // Returns symbolic names or binary combination of symbols if available, returns number otherwise.
  private String decompileNumber(long value, Signatures.Function.Parameter param)
  {
    String retVal = null;

    String ids = param.getIdsRef();
    if (!ids.isEmpty()) {
      IdsMap map = IdsMapCache.get(ids + ".ids");
      if (map != null) {
        IdsMapEntry entry = map.get(value);
        if (entry != null) {
          retVal = getNormalizedSymbol(entry.getSymbol());
        } else if (ids.equals("areatype") ||
                   ids.equals("areaflag") ||
                   ids.equals("bits") ||
                   ids.equals("classmsk") ||
                   ids.equals("crearefl") ||
                   ids.equals("damages") ||
                   ids.equals("doorflag") ||
                   ids.equals("dmgtype") ||
                   ids.equals("extstate") ||
                   ids.equals("invitem") ||
                   ids.equals("itemflag") ||
                   ids.equals("jourtype") ||
                   ids.equals("magespec") ||
                   ids.equals("splcast") ||
                   ids.equals("state") ||
                   ids.equals("wmpflag")) {
          value &= 0xffffffffL;   // converted into unsigned value
          StringBuilder combi = new StringBuilder();
          for (int bit = 0; bit < 32 && value > 0; bit++) {
            long mask = 1L << bit;
            if ((value & mask) == mask) {
              if (combi.length() > 0) {
                combi.append(" | ");
              }
              entry = map.get(mask);
              if (entry != null) {
                combi.append(getNormalizedSymbol(entry.getSymbol()));
              } else {
                combi.append(String.format("0x%X", mask));
              }
              value &= ~mask;
            }
          }
          if (combi.length() == 0) {
            combi.append('0');
          }
          retVal = combi.toString();
        } else if (isGenerateErrors()) {
          idsErrors.put(Integer.valueOf(lineNr), value + " not found in " + ids.toUpperCase(Locale.ENGLISH) + ".IDS");
        }
      } else if (isGenerateErrors()) {
        idsErrors.put(Integer.valueOf(lineNr), "Could not find " + ids.toUpperCase(Locale.ENGLISH) + ".IDS");
      }
    }

    if (retVal == null) {
      retVal = Long.toString((int)value); // treat as signed 32-bit integer
    }

    return retVal;
  }

  // Returns complete string with enclosing double quotes.
  private String decompileString(String value, Signatures.Function.Parameter param)
  {
    StringBuilder sb = new StringBuilder();

    sb.append('"');
    if (value != null) {
      sb.append(value);
    }
    sb.append('"');

    return sb.toString();
  }

  // Returns a descriptive comment without comment tag, returns empty string otherwise.
  private String generateNumberComment(long value, Signatures.Function.Parameter param, boolean enable)
  {
    StringBuilder sb = new StringBuilder();

    if (enable && (isGenerateComments() || isGenerateResourcesUsed())) {
      ResourceEntry entry = null;
      String[] types = param.getResourceType();
      for (String type: types) {
        if (type.equals("TLK")) {
          int intValue = (int)value;
          if (isGenerateComments()) {
            sb.append(getNormalizedString(StringTable.getStringRef(intValue)));
          }
          if (isGenerateResourcesUsed()) {
            strrefsUsed.add(Integer.valueOf(intValue));
          }
          break;
        } else if (type.equals("SPL")) {
          String resRef = org.infinity.resource.spl.Viewer.getResourceName((int)value, true);
          entry = ResourceFactory.getResourceEntry(resRef, true);
          if (entry != null) {
            if (isGenerateResourcesUsed()) {
              resourcesUsed.add(entry);
            }
            if (isGenerateComments()) {
              sb.append(entry.getResourceName()).append(" (").append(entry.getSearchString()).append(')');
            }
            break;
          }
        }
      }
    }

    return sb.toString();
  }

  // Returns a descriptive comment without comment tag, returns empty string otherwise.
  private String generateStringComment(String value, Signatures.Function.Parameter param, boolean enable)
  {
    StringBuilder sb = new StringBuilder();

    if (enable && (isGenerateComments() || isGenerateResourcesUsed()) && !value.isEmpty()) {
      String[] types = param.getResourceType();
      for (String type: types) {
        if (type.equals(Signatures.Function.Parameter.RESTYPE_SCRIPT) &&
            isGenerateComments()) {
          // resolving script name
          Set<ResourceEntry> set = CreMapCache.getCreForScriptName(value);
          if (set != null && !set.isEmpty()) {
            // First available entry should suffice
            ResourceEntry e = set.iterator().next();
            String s = e.getSearchString();
            if (s != null && !s.isEmpty()) {
              sb.append(s);
            } else {
              sb.append(e.getResourceName());
            }
            break;
          }
        } else if (type.equals(Signatures.Function.Parameter.RESTYPE_SPELL_LIST) &&
                   isGenerateComments()) {
          // resolving list of marked spells
          sb.append('[');
          for (int i = 0, cnt = value.length() / 4; i < cnt; i++) {
            if (i > 0) {
              sb.append(", ");
            }
            String snum = value.substring(i*4, i*4 + 4);
            String result = null;
            try {
               long number = Long.parseLong(snum);
               result = getNormalizedSymbol(IdsMapCache.getIdsSymbol("SPELL.IDS", number));
            } catch (NumberFormatException e) {
            }
            if (result == null) {
              sb.append("UNKNOWN_").append(snum);
            }
            sb.append(result);
          }
          sb.append(']');
          break;
        } else if ((Character.isUpperCase(type.charAt(0)) || Character.isDigit(type.charAt(0))) &&
                   value.length() <= 8) {
          if (!type.equals("ARE") || !ScriptInfo.getInfo().isGlobalScope(value)) {
            // resolving resource name
            String resRef = value + '.' + type;
            ResourceEntry entry = ResourceFactory.getResourceEntry(resRef, true);
            if (entry != null) {
              if (isGenerateResourcesUsed()) {
                resourcesUsed.add(entry);
              }
              if (isGenerateComments()) {
                String s = entry.getSearchString();
                if (s != null) {
                  sb.append(s);
                  break;
                }
              }
            }
          }
        }
      }
    }

    return sb.toString();
  }

  private String generateObjectComment(BcsObject object, boolean enable)
  {
    StringBuilder sb = new StringBuilder();

    if (enable && isGenerateComments()) {
      if (!object.name.isEmpty()) {
        Set<ResourceEntry> set = CreMapCache.getCreForScriptName(object.name);
        if (set != null && !set.isEmpty()) {
          // First available entry should suffice
          ResourceEntry e = set.iterator().next();
          String s = e.getSearchString();
          if (s != null && !s.isEmpty()) {
            sb.append(s);
          } else {
            sb.append(e.getResourceName());
          }
        }
      }
    }

    return sb.toString();
  }

  // Symbols with invalid characters are enclosed in five consecutive double quotes
  private String getNormalizedSymbol(String symbol)
  {
    String retVal = null;

    if (symbol != null) {
      final Pattern p = Pattern.compile("([a-zA-Z_][0-9a-zA-Z#_!-]*)|([a-zA-Z#_][a-zA-Z#_!-][0-9a-zA-Z#_!-]*)");
      if (p.matcher(symbol).matches()) {
        retVal = symbol;
      } else {
        retVal = "\"\"\"\"\"" + symbol + "\"\"\"\"\"";
      }
    }

    return retVal;
  }

  // Removes line breaks from strings to prevent parsing errors
  private String getNormalizedString(String string)
  {
    String retVal = null;

    if (string != null) {
      retVal = string.replaceAll("[\r\n]+", " ");
    }

    return retVal;
  }
}
