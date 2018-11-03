// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.bcs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.infinity.gui.BrowserMenuBar;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.bcs.parser.BafNode;
import org.infinity.resource.bcs.parser.BafNodeTransformer;
import org.infinity.resource.bcs.parser.BafNodeTransformer.ScriptNode;
import org.infinity.resource.bcs.parser.BafParser;
import org.infinity.resource.bcs.parser.ParseException;
import org.infinity.resource.bcs.parser.Token;
import org.infinity.resource.bcs.parser.TokenMgrError;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.BOMStringReader;
import org.infinity.util.CreMapCache;
import org.infinity.util.IdsMapCache;
import org.infinity.util.StringTable;
import org.infinity.util.io.StreamUtils;

public class Compiler
{
  private static final HashMap<String, String> tokenSymbolToDescMap = new HashMap<>();
  static {
    // translate internal parser token names into more descriptive names
    tokenSymbolToDescMap.put("<NUMBER_LITERAL>", "<NUMBER>");
    tokenSymbolToDescMap.put("<STRING_LITERAL>", "<STRING>");
    tokenSymbolToDescMap.put("<LPAREN>", "\"(\"");
    tokenSymbolToDescMap.put("<RPAREN>", "\")\"");
    tokenSymbolToDescMap.put("<LBRACKET>", "\"[\"");
    tokenSymbolToDescMap.put("<RBRACKET>", "\"]\"");
    tokenSymbolToDescMap.put("<COMMA>", "\",\"");
    tokenSymbolToDescMap.put("<DOT>", "\".\"");
    tokenSymbolToDescMap.put("<BANG>", "\"!\"");
    tokenSymbolToDescMap.put("<OR>", "\"|\"");
    tokenSymbolToDescMap.put("<MINUS>", "\"-\"");
    tokenSymbolToDescMap.put("<PLUS>", "\"+\"");
    tokenSymbolToDescMap.put("<POUND>", "\"#\"");
  }

  private final SortedSet<ScriptMessage> errors = new TreeSet<>();
  private final SortedSet<ScriptMessage> warnings = new TreeSet<>();

  private Signatures triggers, actions;
  private String source;    // script source
  private String code;      // compiled bytecode
  private ScriptType scriptType;
  private boolean verbose;  // enable to generate more compile warnings
  // global states:
  private int orCount;      // keeps track of triggers following OR()
  private Token orToken;    // token associated with latest OR trigger
  private int lastCode;     // stores the code of the most recently processed trigger or action
  private Token lastToken;  // token associated with lastCode

  public Compiler(String source)
  {
    this(source, ScriptType.BAF);
  }

  public Compiler(String source, ScriptType type)
  {
    this.scriptType = type;
    setVerbose(BrowserMenuBar.getInstance().showMoreCompileWarnings());
    setSource(source);
  }

  /** Returns BAF script source. */
  public String getSource()
  {
    return source;
  }

  /** Set new BAF script source. */
  public void setSource(String source)
  {
    this.source = (source != null) ? source : "";
    reset();
  }

  /** Load new script source from the specified BAF resource entry. */
  public void setSource(ResourceEntry bafEntry) throws Exception
  {
    if (bafEntry == null) {
      throw new NullPointerException();
    }
    ByteBuffer buffer = bafEntry.getResourceBuffer();
    this.source = StreamUtils.readString(buffer, buffer.limit());
    reset();
  }

  /** Returns compiled script byte code. */
  public String getCode()
  {
    if (code == null) {
      compile();
    }
    return code;
  }

  /** Returns currently used script type. */
  public ScriptType getScriptType()
  {
    return scriptType;
  }

  /**
   * Specify new script type.
   * <b>Node:</b> Automatically invalidates previously compile script source.
   */
  public void setScriptType(ScriptType type)
  {
    if (type != scriptType) {
      reset();
      scriptType = type;
    }
  }

  public SortedSet<ScriptMessage> getErrors()
  {
    return errors;
  }

  public SortedSet<ScriptMessage> getWarnings()
  {
    return warnings;
  }

  /** Indicates whether to generate more compile warnings. */
  public boolean isVerbose()
  {
    return verbose;
  }

  /** Set to generate more compile warnings. */
  public void setVerbose(boolean set)
  {
    verbose = set;
  }

  /**
   * Compiles the current script source as if defined as {@code ScriptType.BAF}.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compileScript()
  {
    return compile(ScriptType.BAF);
  }

  /**
   * Compiles the current script source as if defined as {@code ScriptType.Trigger}.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compileTrigger()
  {
    return compile(ScriptType.TRIGGER);
  }

  /**
   * Compiles the current script source as if defined as {@code ScriptType.Action}.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compileAction()
  {
    return compile(ScriptType.ACTION);
  }

  /**
   * Compiles the currently loaded script source into BCS byte code.
   * Uses {@link #getScriptType()} to determine the correct compile action.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compile()
  {
    return compile(scriptType);
  }

  /**
   * Compiles the current script source according to the specified script type.
   * Supported script types: BAF, TRIGGER and ACTION.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compile(ScriptType type)
  {
    switch (type) {
      case BAF:
      case TRIGGER:
      case ACTION:
      {
        reset();
        BafParser parser = new BafParser(type, new BOMStringReader(source));
        try {
          BafNode bafRoot = null;
          try {
            bafRoot = parser.getRoot();
          } catch (ParseException e) {
            errors.add(new ScriptMessage(generateErrorMessage(e), e.currentToken));
          }
          if (bafRoot != null) {
            BafNodeTransformer transformer = new BafNodeTransformer(errors, warnings, isVerbose());
            if (errors.isEmpty()) {
              ScriptNode scriptRoot = transformer.transformParseNodes(bafRoot);
              if (errors.isEmpty()) {
                if (type == ScriptType.BAF) {
                  code = generateScriptCode(scriptRoot);
                } else if (type == ScriptType.TRIGGER) {
                  code = generateTriggerCode(scriptRoot);
                } else {
                  code = generateActionCode(scriptRoot);
                }
              }
            }
          }
        } catch (ParseException e) {
          errors.add(new ScriptMessage(e.getMessage(), e.currentToken));
        } catch (TokenMgrError e) {
          Token token = null;
          try { token = parser.getNextToken(); } catch (Throwable t) {}
          errors.add(new ScriptMessage(e.getMessage(), token));
        } catch (Throwable e) {
          errors.add(new ScriptMessage(e.getMessage()));
        }
        return code;
      }
      default: throw new IllegalArgumentException("Could not determine script type");
    }
  }

  private String generateScriptCode(ScriptNode root)
  {
    if (root == null) {
      throw new NullPointerException();
    }

    StringBuilder sb = new StringBuilder();
    if ("SC".equals(root.type)) {
      generateSC(sb, root);
    }
    return sb.toString();
  }

  private String generateTriggerCode(ScriptNode root)
  {
    if (root == null) {
      throw new NullPointerException();
    }

    StringBuilder sb = new StringBuilder();
    if ("SEQ_TR".equals(root.type)) {
      Iterator<ScriptNode> iter = root.getChildren();
      while (iter.hasNext()) {
        generateTR(sb, iter.next());
      }
    }
    return sb.toString();
  }

  private String generateActionCode(ScriptNode root)
  {
    if (root == null) {
      throw new NullPointerException();
    }

    StringBuilder sb = new StringBuilder();
    if ("SEQ_AC".equals(root.type)) {
      Iterator<ScriptNode> iter = root.getChildren();
      while (iter.hasNext()) {
        generateAC(sb, iter.next());
      }
    }
    return sb.toString();
  }

  private void generateSC(StringBuilder sb, ScriptNode node)
  {
    if ("SC".equals(node.type)) {
      sb.append("SC\n");

      Iterator<ScriptNode> iter = node.getChildren();
      while (iter.hasNext()) {
        generateCR(sb, iter.next());
      }

      sb.append("SC\n");
    } else {
      errors.add(new ScriptMessage("Invalid script source"));
    }
  }

  private void generateCR(StringBuilder sb, ScriptNode node)
  {
    if ("CR".equals(node.type)) {
      sb.append("CR\n");

      Iterator<ScriptNode> iter = node.getChildren();
      while (iter.hasNext()) {
        ScriptNode child = iter.next();
        if ("CO".equals(child.type)) {
          generateCO(sb, child);
        } else if ("RS".equals(child.type)) {
          generateRS(sb, child);
        } else {
          warnings.add(new ScriptMessage("Unexpected block type: " + child.type, child.token));
        }
      }

      sb.append("CR\n");
    } else {
      errors.add(new ScriptMessage("Invalid condition-response block", node.token));
    }
  }

  private void generateCO(StringBuilder sb, ScriptNode node)
  {
    if ("CO".equals(node.type)) {
      sb.append("CO\n");

      Iterator<ScriptNode> iter = node.getChildren();
      while (iter.hasNext()) {
        generateTR(sb, iter.next());
      }

      sb.append("CO\n");
    } else {
      errors.add(new ScriptMessage("Invalid condition block", node.token));
    }
  }

  private void generateRS(StringBuilder sb, ScriptNode node)
  {
    if ("RS".equals(node.type)) {
      // finalizing previous condition block
      if (isOverrideFunction(lastCode, true)) {
        warnings.add(new ScriptMessage("Override trigger should not be last trigger", lastToken));
        lastCode = -1;
        lastToken = null;
      }
      if (orCount > 0) {
        warnings.add(new ScriptMessage("Missing " + orCount + " trigger(s) in order to match OR()", orToken));
        orCount = 0;
        orToken = null;
      }

      sb.append("RS\n");

      Iterator<ScriptNode> iter = node.getChildren();
      while (iter.hasNext()) {
        generateRE(sb, iter.next());
      }

      sb.append("RS\n");
    } else {
      errors.add(new ScriptMessage("Invalid response-set block", node.token));
    }
  }

  private void generateRE(StringBuilder sb, ScriptNode node)
  {
    if ("RE".equals(node.type)) {
      sb.append("RE\n").append(node.code);

      Iterator<ScriptNode> iter = node.getChildren();
      while (iter.hasNext()) {
        generateAC(sb, iter.next());
      }

      sb.append("RE\n");
    } else {
      errors.add(new ScriptMessage("Invalid response block", node.token));
    }
  }

  private void generateTR(StringBuilder sb, ScriptNode node)
  {
    if ("TR".equals(node.type)) {
      BcsTrigger trigger = new BcsTrigger(triggers);

      boolean isOverride = isOverrideFunction((int)node.code, true);
      if (isOverride && lastCode == node.code) {
        warnings.add(new ScriptMessage("Consecutive override triggers: last instance overrides previous instance(s)", node.token));
      }

      boolean isOR = node.function.getName().equals("OR");
      if (orCount > 0) {
        if (isOR) {
          warnings.add(new ScriptMessage("Nested OR() triggers not allowed", node.token));
        }
        if (!isOverride) {
          orCount--;
        }
      }
      if (isOR) {
        if (isOverrideFunction(lastCode, true)) {
          warnings.add(new ScriptMessage("OR() cannot be used inside override triggers", node.token));
        }
      }

      // setting trigger function code
      trigger.id = node.function.getId();

      // setting numbers
      for (int i = 0, cnt = node.numbers.size(); i < cnt; i++) {
        try {
          trigger.setNumericParam(i, node.numbers.get(i));
        } catch (IllegalArgumentException e) {
          warnings.add(new ScriptMessage("Too many numeric arguments (allowed: 3)", node.token));
        }
      }

      // setting strings
      String[] strings = trigger.setStringParams(node.function, node.strings.toArray(new String[node.strings.size()]));
      trigger.t4 = strings[0];
      trigger.t5 = strings[1];

      // setting single object and preparing nested trigger
      Iterator<ScriptNode> iter = node.getChildren();
      ScriptNode ovrTrigger = null;
      BcsObject object = null;
      while (iter.hasNext()) {
        ScriptNode child = iter.next();
        if ("OB".equals(child.type)) {
          if (object == null) {
            object = generateOB(child);
          } else {
            warnings.add(new ScriptMessage("Too many object arguments (allowed: 1)", node.token));
          }
        } else if ("TR".equals(child.type)) {
          if (ovrTrigger == null) {
            ovrTrigger = child;
          } else {
            warnings.add(new ScriptMessage("Too many trigger arguments (allowed: 1)", node.token));
          }
        }
      }
      if (object != null) {
        trigger.t6 = object;
      }

      // setting point
      trigger.t7.x = node.point.x;
      trigger.t7.y = node.point.y;

      // setting negation state
      if (ovrTrigger == null) {
        trigger.negated = node.negated;
      }

      checkParams(node);

      try {
        sb.append(trigger.toByteCode());
        lastCode = trigger.id;
        lastToken = node.token;
      } catch (Exception e) {
        errors.add(new ScriptMessage(e.getMessage(), node.token));
      }

      if (isOR) {
        orCount = (int)trigger.getNumericParam(0);
        orToken = node.token;
      }

      // overridden trigger directly follows override trigger
      if (ovrTrigger != null) {
        ovrTrigger.negated |= node.negated;   // carry negation state over to nested trigger
        generateTR(sb, ovrTrigger);
      }

    } else {
      errors.add(new ScriptMessage("Invalid trigger block", node.token));
    }
  }

  private void generateAC(StringBuilder sb, ScriptNode node)
  {
    generateAC(sb, node, null);
  }

  private void generateAC(StringBuilder sb, ScriptNode node, BcsObject overrideTarget)
  {
    if ("AC".equals(node.type)) {
      // ActionOverride target will be passed to the overridden action
      boolean isOverride = isOverrideFunction((int)node.code, false);
      if (isOverride && overrideTarget != null) {
        errors.add(new ScriptMessage("Nested override actions not allowed", node.token));
        return;
      }

      if (isOverride) {
        // only getting override target
        Iterator<ScriptNode> iter = node.getChildren();
        BcsObject target = null;
        ScriptNode action = null;
        while (iter.hasNext()) {
          ScriptNode child = iter.next();
          if ("OB".equals(child.type)) {
            if (target == null) {
              target = generateOB(child);
            } else {
              warnings.add(new ScriptMessage("Too many override targets (allowed: 1)", node.token));
            }
          } else if ("AC".equals(child.type)) {
            if (action == null) {
              action = child;
            } else {
              warnings.add(new ScriptMessage("Too many action arguments (allowed: 1)", node.token));
            }
          }
        }
        if (target == null) {
          warnings.add(new ScriptMessage("No override target found - using default target", node.token));
          target = BcsObject.getEmptyObject();
          return;
        }
        if (action == null) {
          warnings.add(new ScriptMessage("No action argument found - using default action", node.token));
          action = new ScriptNode(node, "AC", node.token);
          action.code = 0L;
          action.function = actions.getFunction(0)[0];
        }
        generateAC(sb, action, target);
      } else {
        // generating action structure
        BcsAction action = new BcsAction(actions);

        // setting trigger function code
        action.id = node.function.getId();

        // handling objects
        int objIndex = 0;
        action.setObjectParam(objIndex, overrideTarget);
        objIndex++;

        Iterator<ScriptNode> iter = node.getChildren();
        while (iter.hasNext()) {
          ScriptNode child = iter.next();
          if ("OB".equals(child.type)) {
            try {
              action.setObjectParam(objIndex, generateOB(child));
            } catch (IllegalArgumentException e) {
              warnings.add(new ScriptMessage("Too many object arguments (allowed: 2)", node.token));
              break;
            }
            objIndex++;
          }
        }

        // handling numbers
        for (int i = 0, cnt = node.numbers.size(); i < cnt; i++) {
          try {
            action.setNumericParam(i, node.numbers.get(i).longValue());
          } catch (IllegalArgumentException e) {
            warnings.add(new ScriptMessage("Too many numeric arguments (allowed: 3)", node.token));
            break;
          }
        }

        // handling strings
        String[] strings = action.setStringParams(node.function, node.strings.toArray(new String[node.strings.size()]));
        action.a8 = strings[0];
        action.a9 = strings[1];

        // handling points
        action.a5.x = node.point.x;
        action.a5.y = node.point.y;

        checkParams(node);

        try {
          sb.append(action.toByteCode());
          lastCode = action.id;
          lastToken = node.token;
        } catch (Exception e) {
          errors.add(new ScriptMessage(e.getMessage(), node.token));
        }
      }
    } else {
      errors.add(new ScriptMessage("Invalid action block", node.token));
    }
  }

  private BcsObject generateOB(ScriptNode node)
  {
    BcsObject object = null;
    if ("OB".equals(node.type)) {
      object = new BcsObject();

      // IDS targets
      String[] targetIds = ScriptInfo.getInfo().getObjectIdsList();
      for (int i = 0, cnt = node.numbers.size(); i < cnt; i++) {
        try {
          object.setTargetValue(i, node.numbers.get(i));
        } catch (IllegalArgumentException e) {
          warnings.add(new ScriptMessage("Too many IDS targets (allowed: " + targetIds.length + ")", node.token));
          break;
        }
      }

      // Target identifiers
      for (int i = 0, cnt = node.numbers2.size(); i < cnt; i++) {
        // added in reverse order!
        try {
          object.setIdentifierValue(i, node.numbers2.get(node.numbers2.size() - i - 1));
        } catch (IllegalArgumentException e) {
          warnings.add(new ScriptMessage("Too many target identifiers (allowed: " + object.identifier.length + ")", node.token));
          break;
        }
      }

      // Script name
      if (!node.strings.isEmpty()) {
        object.name = node.strings.get(0);
      }

      // Target region
      object.region.x = node.region.x;
      object.region.y = node.region.y;
      object.region.width = node.region.width;
      object.region.height = node.region.height;

      checkParams(node);
    }
    return object;
  }

  // Determines whether the specified node points to an override trigger or action function
  private boolean isOverrideFunction(int code, boolean isTrigger)
  {
    boolean retVal = false;

    Signatures.Function[] funcs = isTrigger ? triggers.getFunction(code) : actions.getFunction(code);
    if (funcs != null) {
      for (Signatures.Function func: funcs) {
        char paramType = 0;
        if (isTrigger) {
          paramType = Signatures.Function.Parameter.TYPE_TRIGGER;
        } else {
          paramType = Signatures.Function.Parameter.TYPE_ACTION;
        }

        if (paramType != 0 && func != null) {
          for (int i = 0, cnt = func.getNumParameters(); i < cnt; i++) {
            if (func.getParameter(i).getType() == paramType) {
              retVal = true;
              break;
            }
          }
        }

        if (retVal) {
          break;
        }
      }
    }

    return retVal;
  }

  private void reset()
  {
    errors.clear();
    warnings.clear();
    code = null;
    orCount = 0;
    orToken = null;
    lastCode = 0;
    lastToken = null;
    if ((triggers = Signatures.getTriggers()) == null) {
      errors.add(new ScriptMessage("Error loading trigger signatures"));
    }
    if ((actions = Signatures.getActions()) == null) {
      errors.add(new ScriptMessage("Error loading action signatures"));
    }
  }

  // Checks all available arguments of the specified action, trigger or object node
  private void checkParams(ScriptNode node)
  {
    if (node == null) {
      return;
    }

    if ("TR".equals(node.type) || "AC".equals(node.type)) {
      Signatures.Function function = node.function;
      for (int i = 0, sidx = 0, nidx = 0, cnt = function.getNumParameters(); i < cnt; i++) {
        Signatures.Function.Parameter param = function.getParameter(i);
        if (param.getType() == Signatures.Function.Parameter.TYPE_STRING &&
            sidx < node.strings.size()) {
          String value = node.strings.get(sidx);
          sidx++;
          String[] resTypes = param.getResourceType();
          if (resTypes.length > 0) {
            boolean found = false;
            for (String resType: resTypes) {
              if (resType.equals(Signatures.Function.Parameter.RESTYPE_SCRIPT)) {
                // checking script name
                checkScriptName(param, value, node);
                found = true;
                break;
              } else if (resType.equals(Signatures.Function.Parameter.RESTYPE_SPELL_LIST)) {
                // checking sequence of SPELL.IDS codes
                checkSpellList(param, value, node);
                found = true;
                break;
              } else if (Character.isUpperCase(resType.charAt(0)) || Character.isDigit(resType.charAt(0)) ) {
                if (value.length() <= 8) {
                  // checking resource
                  if (value.isEmpty()) {
                    // ignore empty string
                    found = true;
                    break;
                  } else if (resType.equals("ARE") && ScriptInfo.getInfo().isGlobalScope(value)) {
                    // scopes always pass the check
                    found = true;
                    break;
                  } else {
                    String resName = value + '.' + resType;
                    if (ResourceFactory.resourceExists(resName, true)) {
                      if (param.isCombinedString() && !param.isColonSeparatedString() &&
                          value.length() != 6) {
                        // special: fixed length scope argument required
                        errors.add(new ScriptMessage("Fixed resource name length of 6 characters required: " + param.toString(true) + " - " + value, node.token));
                      }
                      found = true;
                      break;
                    }
                  }
                } else {
                  int diff = value.length() - 8;
                  warnings.add(new ScriptMessage("Resource name too long by " + diff + " character(s): " + param.toString(true) + " - " + value, node.token));
                  found = true;
                  break;
                }
              }
            }
            if (!found) {
              warnings.add(new ScriptMessage("Resource not found: " + param.toString(true) + " - " + value, node.token));
            }
          }
        } else if (param.getType() == Signatures.Function.Parameter.TYPE_INTEGER &&
                   nidx < node.numbers.size()) {
          long value = node.numbers.get(nidx);
          nidx++;
          String[] types = param.getResourceType();
          if (types.length > 0) {
            for (String type: types) {
              if (type.equals("TLK")) {
                checkStrref(value, node);
              } else if (type.equals("SPL")) {
                checkSpellCode(value, node);
              }
            }
          }
        }
      }
    } else if ("OB".equals(node.type)) {
      if (!node.strings.isEmpty()) {
        checkScriptName(node.parameter, node.strings.get(0), node);
      }
    }
  }

  private void checkScriptName(Signatures.Function.Parameter param, String name, ScriptNode node)
  {
    if (!name.isEmpty() &&
        !CreMapCache.hasCreScriptName(name) &&
        !CreMapCache.hasAreScriptName(name)) {
      if (param != null) {
        warnings.add(new ScriptMessage("Script name not found: " + param.toString(true) + " - " + name, node.token));
      } else {
        warnings.add(new ScriptMessage("Script name not found: " + name, node.token));
      }
    }
  }

  private void checkSpellList(Signatures.Function.Parameter param, String list, ScriptNode node)
  {
    if ((list.length() & 3) == 0) {
      for (int j = 0, splcount = list.length() / 4; j < splcount; j++) {
        String snum = list.substring(j*4, j*4 + 4);
        try {
          long number = Long.parseLong(snum);
          String symbol = IdsMapCache.getIdsSymbol("SPELL.IDS", number);
          if (symbol != null) {
            String resName = org.infinity.resource.spl.Viewer.getResourceName((int)number, true);
            if (!ResourceFactory.resourceExists(resName)) {
              if (param != null) {
                warnings.add(new ScriptMessage("Resource not found: " + param.toString(true) + " - " + resName, node.token));
              } else {
                warnings.add(new ScriptMessage("Resource not found: " + resName, node.token));
              }
            }
          } else {
            warnings.add(new ScriptMessage("No symbol associated with spell code: " + snum, node.token));
          }
        } catch (NumberFormatException e) {
          warnings.add(new ScriptMessage("Invalid spell identifier: " + snum, node.token));
        }
      }
    }
  }

  private void checkStrref(long value, ScriptNode node)
  {
    if (value != -1 && !StringTable.isValidStringRef((int)value)) {
      warnings.add(new ScriptMessage("String reference is out of range: " + value, node.token));
    }
  }

  private void checkSpellCode(long value, ScriptNode node)
  {
    if (value != 0) {
      String symbol = IdsMapCache.getIdsSymbol("SPELL.IDS", value);
      String resRef = org.infinity.resource.spl.Viewer.getResourceName((int)value, true);
      if (!ResourceFactory.resourceExists(resRef)) {
        if (symbol == null) {
          symbol = Long.toString(value);
        }
        if (resRef != null && !resRef.isEmpty()) {
          warnings.add(new ScriptMessage("Spell resource for " + symbol + " not found: " + resRef, node.token));
        } else {
          warnings.add(new ScriptMessage("Invalid spell code: " + symbol, node.token));
        }
      }
    }
  }

  // Generates a more user-friendly error message
  private String generateErrorMessage(ParseException e)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("Syntax error at line ").append(e.currentToken.next.beginLine);
    sb.append(", column ").append(e.currentToken.next.beginColumn);
    sb.append(". Expected ");

    ArrayList<String> expected = new ArrayList<>();
    for (int i = 0; i < e.expectedTokenSequences.length; i++) {
      String symbol = "";
      for (int j = 0; j < e.expectedTokenSequences[i].length; j++) {
        String s = tokenSymbolToDescMap.get(e.tokenImage[e.expectedTokenSequences[i][j]]);
        if (s == null) {
          s = e.tokenImage[e.expectedTokenSequences[i][j]];
        }
        symbol += s + ' ';
      }
      if (!expected.contains(symbol)) {
        expected.add(symbol);
      }
    }

    for (int i = 0; i < expected.size(); i++) {
      if (i > 0) {
        if (i < expected.size() - 1) {
          sb.append(" , ");
        } else {
          sb.append(" or ");
        }
      }
      sb.append(expected.get(i));
    }

    return sb.toString();
  }
}
