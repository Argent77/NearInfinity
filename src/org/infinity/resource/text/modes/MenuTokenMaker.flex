// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

/*
 * 22/04/2024
 *
 * MenuTokenMaker.java - Modified version of LuaTokenMaker.java that provides
 * additional supported for Menu-specific scripting.
 *
 * This library is distributed under a modified BSD license.  See the included
 * LICENSE file for details.
 */
package org.infinity.resource.text.modes;

import java.io.*;
import javax.swing.text.Segment;

import org.fife.ui.rsyntaxtextarea.*;


/**
 * Scanner for the Menu scripting language extension.<p>
 *
 * This implementation was created using
 * <a href="https://www.jflex.de/">JFlex</a> 1.4.1; however, the generated file
 * was modified for performance.  Memory allocation needs to be almost
 * completely removed to be competitive with the handwritten lexers (subclasses
 * of <code>AbstractTokenMaker</code>), so this class has been modified so that
 * Strings are never allocated (via yytext()), and the scanner never has to
 * worry about refilling its buffer (needlessly copying chars around).
 * We can achieve this because RText always scans exactly 1 line of tokens at a
 * time, and hands the scanner this line as an array of characters (a Segment
 * really).  Since tokens contain pointers to char arrays instead of Strings
 * holding their contents, there is no need for allocating new memory for
 * Strings.<p>
 *
 * The actual algorithm generated for scanning has, of course, not been
 * modified.<p>
 *
 * If you wish to regenerate this file yourself, keep in mind the following:
 * <ul>
 *   <li>The generated <code>MenuTokenMaker.java</code> file will contain two
 *       definitions of both <code>zzRefill</code> and <code>yyreset</code>.
 *       You should hand-delete the second of each definition (the ones
 *       generated by the lexer), as these generated methods modify the input
 *       buffer, which we'll never have to do.</li>
 *   <li>You should also change the declaration/definition of zzBuffer to NOT
 *       be initialized.  This is a needless memory allocation for us since we
 *       will be pointing the array somewhere else anyway.</li>
 *   <li>You should NOT call <code>yylex()</code> on the generated scanner
 *       directly; rather, you should use <code>getTokenList</code> as you would
 *       with any other <code>TokenMaker</code> instance.</li>
 * </ul>
 *
 * @author Robert Futrell
 * @version 0.4
 *
 */
%%

%public
%class MenuTokenMaker
%extends AbstractJFlexTokenMaker
%unicode
%ignorecase
%type org.fife.ui.rsyntaxtextarea.Token


%{

  /** Style for highlighting MENU scripts. */
  public static final String SYNTAX_STYLE_MENU = "text/MENU";

  /**
   * Constructor.  This must be here because JFlex does not generate a
   * no-parameter constructor.
   */
  public MenuTokenMaker() {
  }


  /**
   * Adds the token specified to the current linked list of tokens.
   *
   * @param tokenType The token's type.
   */
  private void addToken(int tokenType) {
    addToken(zzStartRead, zzMarkedPos-1, tokenType);
  }


  /**
   * Adds the token specified to the current linked list of tokens.
   *
   * @param tokenType The token's type.
   */
  private void addToken(int start, int end, int tokenType) {
    int so = start + offsetShift;
    addToken(zzBuffer, start,end, tokenType, so);
  }


  /**
   * Adds the token specified to the current linked list of tokens.
   *
   * @param array The character array.
   * @param start The starting offset in the array.
   * @param end The ending offset in the array.
   * @param tokenType The token's type.
   * @param startOffset The offset in the document at which this token
   *                    occurs.
   */
  @Override
  public void addToken(char[] array, int start, int end, int tokenType, int startOffset) {
    super.addToken(array, start,end, tokenType, startOffset);
    zzStartRead = zzMarkedPos;
  }


  @Override
  public String[] getLineCommentStartAndEnd(int languageIndex) {
    return new String[] { "--", null };
  }


  /**
   * Returns the first token in the linked list of tokens generated
   * from <code>text</code>.  This method must be implemented by
   * subclasses so they can correctly implement syntax highlighting.
   *
   * @param text The text from which to get tokens.
   * @param initialTokenType The token type we should start with.
   * @param startOffset The offset into the document at which
   *        <code>text</code> starts.
   * @return The first <code>Token</code> in a linked list representing
   *         the syntax highlighted text.
   */
  public Token getTokenList(Segment text, int initialTokenType, int startOffset) {

    resetTokenList();
    this.offsetShift = -text.offset + startOffset;

    // Start off in the proper state.
    int state = Token.NULL;
    switch (initialTokenType) {
      case Token.COMMENT_MULTILINE:
        state = MLC;
        start = text.offset;
        break;
      case Token.LITERAL_BACKQUOTE:
        state = LONGSTRING;
        start = text.offset;
        break;
      case Token.LITERAL_STRING_DOUBLE_QUOTE:
        state = STRING;
        start = text.offset;
        break;
      default:
        state = Token.NULL;
    }

    s = text;
    try {
      yyreset(zzReader);
      yybegin(state);
      return yylex();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      return new TokenImpl();
    }

  }


  /**
   * Refills the input buffer.
   *
   * @return      <code>true</code> if EOF was reached, otherwise
   *              <code>false</code>.
   */
  private boolean zzRefill() {
    return zzCurrentPos>=s.offset+s.count;
  }


  /**
   * Resets the scanner to read from a new input stream.
   * Does not close the old reader.
   *
   * All internal variables are reset, the old input stream 
   * <b>cannot</b> be reused (internal buffer is discarded and lost).
   * Lexical state is set to <tt>YY_INITIAL</tt>.
   *
   * @param reader   the new input stream 
   */
  public final void yyreset(Reader reader) {
    // 's' has been updated.
    zzBuffer = s.array;
    /*
     * We replaced the line below with the two below it because zzRefill
     * no longer "refills" the buffer (since the way we do it, it's always
     * "full" the first time through, since it points to the segment's
     * array).  So, we assign zzEndRead here.
     */
    //zzStartRead = zzEndRead = s.offset;
    zzStartRead = s.offset;
    zzEndRead = zzStartRead + s.count - 1;
    zzCurrentPos = zzMarkedPos = zzPushbackPos = s.offset;
    zzLexicalState = YYINITIAL;
    zzReader = reader;
    zzAtBOL  = true;
    zzAtEOF  = false;
  }


%}

Letter                = [A-Za-z_]
Digit                 = [0-9]

LineTerminator        = (\n)
WhiteSpace            = ([ \t\f])

UnclosedCharLiteral   = ([\']([^\'\n]|"\\'")*)
CharLiteral           = ({UnclosedCharLiteral}"'")

StringDelimiter       = ([\"])

LongStringBegin       = ("[[")
LongStringEnd         = ("]]")

LineCommentBegin      = ("--")
MLCBegin              = ({LineCommentBegin}{LongStringBegin})

Number                = ( "."? {Digit} ({Digit}|".")* ([eE][+-]?)? ({Letter}|{Digit})* )
BooleanLiteral        = ("true"|"false")

Separator             = ([\(\)\{\}\[\]\]])
Separator2            = ([\;:,.])

ArithmeticOperator    = ("+"|"-"|"*"|"/"|"^"|"%")
RelationalOperator    = ("<"|">"|"<="|">="|"=="|"~=")
LogicalOperator       = ("and"|"or"|"not"|"#")
ConcatenationOperator = ("..")
Elipsis               = ({ConcatenationOperator}".")
Operator              = ({ArithmeticOperator}|{RelationalOperator}|{LogicalOperator}|{ConcatenationOperator}|{Elipsis})

Identifier            = ({Letter}({Letter}|{Digit})*)

LuaBlockDelimiter     = ([`])


%state MLC
%state LONGSTRING
%state STRING
%state LINECOMMENT


%%

/* Lua keywords */
<YYINITIAL> "break" |
<YYINITIAL> "do" |
<YYINITIAL> "else" |
<YYINITIAL> "elseif" |
<YYINITIAL> "end" |
<YYINITIAL> "for" |
<YYINITIAL> "function" |
<YYINITIAL> "goto" |
<YYINITIAL> "if" |
<YYINITIAL> "in" |
<YYINITIAL> "local" |
<YYINITIAL> "nil" |
<YYINITIAL> "repeat" |
<YYINITIAL> "return" |
<YYINITIAL> "then" |
<YYINITIAL> "until" |
<YYINITIAL> "while" { addToken(Token.RESERVED_WORD); }

/* Menu keywords */
<YYINITIAL> "action" |
<YYINITIAL> "actionHold" |
<YYINITIAL> "actionScroll" |
<YYINITIAL> "actionUpdate" |
<YYINITIAL> "actionalt" |
<YYINITIAL> "actionbar" |
<YYINITIAL> "actiondbl" |
<YYINITIAL> "actiondrag" |
<YYINITIAL> "actionenter" |
<YYINITIAL> "actionexit" |
<YYINITIAL> "actionsimpledrag" |
<YYINITIAL> "actionsimpledrop" |
<YYINITIAL> "align" |
<YYINITIAL> "area" |
<YYINITIAL> "areamap" |
<YYINITIAL> "background" |
<YYINITIAL> "bam" |
<YYINITIAL> "bitmap" |
<YYINITIAL> "bottom" |
<YYINITIAL> "boundingPoints" |
<YYINITIAL> "button" |
<YYINITIAL> "center" |
<YYINITIAL> "clickable" |
<YYINITIAL> "clunkyScroll" |
<YYINITIAL> "color" |
<YYINITIAL> "colordisplay" |
<YYINITIAL> "column" |
<YYINITIAL> "count" |
<YYINITIAL> "dynamic" |
<YYINITIAL> "edit" |
<YYINITIAL> "enabled" |
<YYINITIAL> "encumbrance" |
<YYINITIAL> "escape" |
<YYINITIAL> "exclusiveFocus" |
<YYINITIAL> "fill" |
<YYINITIAL> "focus" |
<YYINITIAL> "font" |
<YYINITIAL> "force" |
<YYINITIAL> "frame" |
<YYINITIAL> "frameTimes" |
<YYINITIAL> "full" |
<YYINITIAL> "func" |
<YYINITIAL> "glow" |
<YYINITIAL> "greyscale" |
<YYINITIAL> "halign" |
<YYINITIAL> "handle" |
<YYINITIAL> "healthbar" |
<YYINITIAL> "height" |
<YYINITIAL> "hide" |
<YYINITIAL> "hidehighlight" |
<YYINITIAL> "highlight" |
<YYINITIAL> "highlightgroup" |
<YYINITIAL> "icon" |
<YYINITIAL> "ignoreEsc" |
<YYINITIAL> "ignoreEvents" |
<YYINITIAL> "ignoreFocus" |
<YYINITIAL> "ignoreNav" |
<YYINITIAL> "indent" |
<YYINITIAL> "interactable" |
<YYINITIAL> "item" |
<YYINITIAL> "label" |
<YYINITIAL> "left" |
<YYINITIAL> "list" |
<YYINITIAL> "loop" |
<YYINITIAL> "lower" |
<YYINITIAL> "lua" |
<YYINITIAL> "map" |
<YYINITIAL> "maxchars" |
<YYINITIAL> "maxlines" |
<YYINITIAL> "memorizedSpellInfo" |
<YYINITIAL> "menu" |
<YYINITIAL> "modal" |
<YYINITIAL> "mosaic" |
<YYINITIAL> "movie" |
<YYINITIAL> "name" |
<YYINITIAL> "noBorder" |
<YYINITIAL> "offset" |
<YYINITIAL> "on" |
<YYINITIAL> "onclose" |
<YYINITIAL> "onopen" |
<YYINITIAL> "opacity" |
<YYINITIAL> "overlayTint" |
<YYINITIAL> "pad" |
<YYINITIAL> "pagedown" |
<YYINITIAL> "pageup" |
<YYINITIAL> "palette" |
<YYINITIAL> "panel" |
<YYINITIAL> "paperdoll" |
<YYINITIAL> "placeholder" |
<YYINITIAL> "point" |
<YYINITIAL> "popupSound" |
<YYINITIAL> "portrait" |
<YYINITIAL> "position" |
<YYINITIAL> "progressbar" |
<YYINITIAL> "pulse" |
<YYINITIAL> "queuedMovie" |
<YYINITIAL> "rectangle" |
<YYINITIAL> "respectClipping" |
<YYINITIAL> "respectConstraints" |
<YYINITIAL> "right" |
<YYINITIAL> "rowbackground" |
<YYINITIAL> "rowheight" |
<YYINITIAL> "rowwidth" |
<YYINITIAL> "scaleToClip" |
<YYINITIAL> "scrollbar" |
<YYINITIAL> "seperator" |
<YYINITIAL> "sequence" |
<YYINITIAL> "sequenceonce" |
<YYINITIAL> "settings" |
<YYINITIAL> "shadow" |
<YYINITIAL> "size" |
<YYINITIAL> "skipReset" |
<YYINITIAL> "slider" |
<YYINITIAL> "sliderBackground" |
<YYINITIAL> "slot" |
<YYINITIAL> "slotinfo" |
<YYINITIAL> "sound" |
<YYINITIAL> "spellInfo" |
<YYINITIAL> "state" |
<YYINITIAL> "style" |
<YYINITIAL> "subtitle" |
<YYINITIAL> "table" |
<YYINITIAL> "template" |
<YYINITIAL> "text" |
<YYINITIAL> "tint" |
<YYINITIAL> "toggle" |
<YYINITIAL> "tooltip" |
<YYINITIAL> "top" |
<YYINITIAL> "transparent" |
<YYINITIAL> "upper" |
<YYINITIAL> "usages" |
<YYINITIAL> "useFontZoom" |
<YYINITIAL> "useOverlayTint" |
<YYINITIAL> "usealpha" |
<YYINITIAL> "valid" |
<YYINITIAL> "valign" |
<YYINITIAL> "var" |
<YYINITIAL> "width" |
<YYINITIAL> "worldmap"          { addToken(Token.RESERVED_WORD_2); }

/* Data types. */
<YYINITIAL> "<number>" |
<YYINITIAL> "<name>" |
<YYINITIAL> "<string>" |
<YYINITIAL> "<eof>" |
<YYINITIAL> "NULL"              { addToken(Token.DATA_TYPE); }

/* Lua functions. */
<YYINITIAL> "_G" |
<YYINITIAL> "_VERSION" |
<YYINITIAL> "assert" |
<YYINITIAL> "collectgarbage" |
<YYINITIAL> "dofile" |
<YYINITIAL> "error" |
<YYINITIAL> "getfenv" |
<YYINITIAL> "getmetatable" |
<YYINITIAL> "ipairs" |
<YYINITIAL> "load" |
<YYINITIAL> "loadfile" |
<YYINITIAL> "loadstring" |
<YYINITIAL> "module" |
<YYINITIAL> "next" |
<YYINITIAL> "pairs" |
<YYINITIAL> "pcall" |
<YYINITIAL> "print" |
<YYINITIAL> "rawequal" |
<YYINITIAL> "rawget" |
<YYINITIAL> "rawset" |
<YYINITIAL> "require" |
<YYINITIAL> "select" |
<YYINITIAL> "setfenv" |
<YYINITIAL> "setmetatable" |
<YYINITIAL> "tonumber" |
<YYINITIAL> "tostring" |
<YYINITIAL> "type" |
<YYINITIAL> "unpack" |
<YYINITIAL> "xpcall" |
/* Infinity Engine functions */
<YYINITIAL> "Infinity_ActivateInventory" |
<YYINITIAL> "Infinity_ActivateRecord" |
<YYINITIAL> "Infinity_AddDLC" |
<YYINITIAL> "Infinity_AddDLCContent" |
<YYINITIAL> "Infinity_CanCloudSave" |
<YYINITIAL> "Infinity_CanLevelUp" |
<YYINITIAL> "Infinity_ChangeOption" |
<YYINITIAL> "Infinity_CheckItemIdentify" |
<YYINITIAL> "Infinity_ClickItem" |
<YYINITIAL> "Infinity_ClickObjectInWorld" |
<YYINITIAL> "Infinity_ClickScreen" |
<YYINITIAL> "Infinity_ClickWorldAt" |
<YYINITIAL> "Infinity_CloseEngine" |
<YYINITIAL> "Infinity_DestroyAnimation" |
<YYINITIAL> "Infinity_DisplayString" |
<YYINITIAL> "Infinity_DoFile" |
<YYINITIAL> "Infinity_EnterEdit" |
<YYINITIAL> "Infinity_FetchString" |
<YYINITIAL> "Infinity_FindItemWithBam" |
<YYINITIAL> "Infinity_FindItemWithText" |
<YYINITIAL> "Infinity_FindUIItemByName" |
<YYINITIAL> "Infinity_FocusTextEdit" |
<YYINITIAL> "Infinity_GetClockTicks" |
<YYINITIAL> "Infinity_GetContainerItemDescription" |
<YYINITIAL> "Infinity_GetContentHeight" |
<YYINITIAL> "Infinity_GetCurrentGroundPage" |
<YYINITIAL> "Infinity_GetCurrentMovie" |
<YYINITIAL> "Infinity_GetCurrentScreenName" |
<YYINITIAL> "Infinity_GetFilesOfType" |
<YYINITIAL> "Infinity_GetFrameCounter" |
<YYINITIAL> "Infinity_GetGameTicks" |
<YYINITIAL> "Infinity_GetGroundItemDescription" |
<YYINITIAL> "Infinity_GetGroupItemDescription" |
<YYINITIAL> "Infinity_GetINIString" |
<YYINITIAL> "Infinity_GetINIValue" |
<YYINITIAL> "Infinity_GetInCutsceneMode" |
<YYINITIAL> "Infinity_GetListHeight" |
<YYINITIAL> "Infinity_GetMaxChapterPage" |
<YYINITIAL> "Infinity_GetMaxGroundPage" |
<YYINITIAL> "Infinity_GetMenuArea" |
<YYINITIAL> "Infinity_GetMenuItemByName" |
<YYINITIAL> "Infinity_GetMousePosition" |
<YYINITIAL> "Infinity_GetNumCharacters" |
<YYINITIAL> "Infinity_GetOffset" |
<YYINITIAL> "Infinity_GetOption" |
<YYINITIAL> "Infinity_GetPasswordRequired" |
<YYINITIAL> "Infinity_GetPortraitTooltip" |
<YYINITIAL> "Infinity_GetScreenSize" |
<YYINITIAL> "Infinity_GetScriptVarInt" |
<YYINITIAL> "Infinity_GetScrollIdentifyEnabled" |
<YYINITIAL> "Infinity_GetSelectedCharacterName" |
<YYINITIAL> "Infinity_GetSpellIdentifyEnabled" |
<YYINITIAL> "Infinity_GetTimeString" |
<YYINITIAL> "Infinity_GetTransitionInProgress" |
<YYINITIAL> "Infinity_GetUseButtonText" |
<YYINITIAL> "Infinity_GooglePlaySignedIn" |
<YYINITIAL> "Infinity_HighlightJournalButton" |
<YYINITIAL> "Infinity_HoverMouseOver" |
<YYINITIAL> "Infinity_HoverMouseOverObject" |
<YYINITIAL> "Infinity_InstanceAnimation" |
<YYINITIAL> "Infinity_IsItemEnabled" |
<YYINITIAL> "Infinity_IsMenuOnStack" |
<YYINITIAL> "Infinity_IsPlayerMoving" |
<YYINITIAL> "Infinity_JoinMultiplayerGame" |
<YYINITIAL> "Infinity_LaunchURL" |
<YYINITIAL> "Infinity_LevelUp" |
<YYINITIAL> "Infinity_Log" |
<YYINITIAL> "Infinity_LookAtObjectInWorld" |
<YYINITIAL> "Infinity_LuaConsoleInput" |
<YYINITIAL> "Infinity_OnAddUserEntry" |
<YYINITIAL> "Infinity_OnCharacterImportItemSelect" |
<YYINITIAL> "Infinity_OnCharacterItemSelect" |
<YYINITIAL> "Infinity_OnEditUserEntry" |
<YYINITIAL> "Infinity_OnGroundPage" |
<YYINITIAL> "Infinity_OnPortraitDblClick" |
<YYINITIAL> "Infinity_OnPortraitItemSelect" |
<YYINITIAL> "Infinity_OnPortraitLClick" |
<YYINITIAL> "Infinity_OnPortraitRClick" |
<YYINITIAL> "Infinity_OnRemoveUserEntry" |
<YYINITIAL> "Infinity_OnScriptItemSelect" |
<YYINITIAL> "Infinity_OnScrollIdentify" |
<YYINITIAL> "Infinity_OnSoundItemSelect" |
<YYINITIAL> "Infinity_OnSpellIdentify" |
<YYINITIAL> "Infinity_OnUseButtonClick" |
<YYINITIAL> "Infinity_OpenInventoryContainer" |
<YYINITIAL> "Infinity_PlayMovie" |
<YYINITIAL> "Infinity_PlaySound" |
<YYINITIAL> "Infinity_PopMenu" |
<YYINITIAL> "Infinity_PressKeyboardButton" |
<YYINITIAL> "Infinity_PushMenu" |
<YYINITIAL> "Infinity_RandomNumber" |
<YYINITIAL> "Infinity_RemoveINIEntry" |
<YYINITIAL> "Infinity_RequestMultiplayerGameDetails" |
<YYINITIAL> "Infinity_ScaleToText" |
<YYINITIAL> "Infinity_ScrollLists" |
<YYINITIAL> "Infinity_SelectDialogueOption" |
<YYINITIAL> "Infinity_SelectItemAbility" |
<YYINITIAL> "Infinity_SelectListItem" |
<YYINITIAL> "Infinity_SendChatMessage" |
<YYINITIAL> "Infinity_SetArea" |
<YYINITIAL> "Infinity_SetBackground" |
<YYINITIAL> "Infinity_SetCloudEnabled" |
<YYINITIAL> "Infinity_SetGooglePlaySigninState" |
<YYINITIAL> "Infinity_SetHairColor" |
<YYINITIAL> "Infinity_SetHighlightColors" |
<YYINITIAL> "Infinity_SetINIValue" |
<YYINITIAL> "Infinity_SetKey" |
<YYINITIAL> "Infinity_SetLanguage" |
<YYINITIAL> "Infinity_SetMajorColor" |
<YYINITIAL> "Infinity_SetMinorColor" |
<YYINITIAL> "Infinity_SetOffset" |
<YYINITIAL> "Infinity_SetOverlay" |
<YYINITIAL> "Infinity_SetScreenSize" |
<YYINITIAL> "Infinity_SetScrollTop" |
<YYINITIAL> "Infinity_SetSkinColor" |
<YYINITIAL> "Infinity_SetToken" |
<YYINITIAL> "Infinity_ShutdownGame" |
<YYINITIAL> "Infinity_SignInOutButtonEnabled" |
<YYINITIAL> "Infinity_SplitItemStack" |
<YYINITIAL> "Infinity_StartItemCapture" |
<YYINITIAL> "Infinity_StartKeybind" |
<YYINITIAL> "Infinity_StopItemCapture" |
<YYINITIAL> "Infinity_StopKeybind" |
<YYINITIAL> "Infinity_StopMovie" |
<YYINITIAL> "Infinity_SwapSlot" |
<YYINITIAL> "Infinity_SwapWithAppearance" |
<YYINITIAL> "Infinity_SwapWithPortrait" |
<YYINITIAL> "Infinity_TakeScreenshot" |
<YYINITIAL> "Infinity_TextEditHasFocus" |
<YYINITIAL> "Infinity_TransitionMenu" |
<YYINITIAL> "Infinity_UpdateCharacterRecordExportPanel" |
<YYINITIAL> "Infinity_UpdateCloudSaveState" |
<YYINITIAL> "Infinity_UpdateInventoryRequesterPanel" |
<YYINITIAL> "Infinity_UpdateLuaStats" |
<YYINITIAL> "Infinity_UpdateStoreMainPanel" |
<YYINITIAL> "Infinity_UpdateStoreRequesterPanel" |
<YYINITIAL> "Infinity_WriteINILine"     { addToken(Token.FUNCTION); }

/* Booleans */
<YYINITIAL> {BooleanLiteral}    { addToken(Token.LITERAL_BOOLEAN); }


<YYINITIAL> {

  {LineTerminator}            { addNullToken(); return firstToken; }

  {WhiteSpace}+               { addToken(Token.WHITESPACE); }

  /* String/Character literals. */
  {CharLiteral}               { addToken(Token.LITERAL_CHAR); }
  {UnclosedCharLiteral}       { addToken(Token.ERROR_CHAR); addNullToken(); return firstToken; }

  {StringDelimiter}           { start = zzMarkedPos-1; yybegin(STRING); }

  {LongStringBegin}           { start = zzMarkedPos-2; yybegin(LONGSTRING); }

  /* Comment literals. */
  {MLCBegin}                  { start = zzMarkedPos-4; yybegin(MLC); }
  {LineCommentBegin}          { start = zzMarkedPos-2; yybegin(LINECOMMENT); }

  /* Separators. */
  {Separator}                 { addToken(Token.SEPARATOR); }
  {Separator2}                { addToken(Token.IDENTIFIER); }

  /* Operators. */
  {Operator}                  { addToken(Token.OPERATOR); }

  /* Identifiers - Comes after Operators for "and", "not" and "or". */
  {Identifier}                { addToken(Token.IDENTIFIER); }

  /* Numbers */
  {Number}                    { addToken(Token.LITERAL_NUMBER_FLOAT); }

  /* Lua block delimiter */
  {LuaBlockDelimiter}         { addToken(Token.PREPROCESSOR); }

  /* Ended with a line not in a string or comment. */
  <<EOF>>                     { addNullToken(); return firstToken; }

  /* Catch any other (unhandled) characters. */
  .                           { addToken(Token.IDENTIFIER); }

}


<MLC> {
  [^\n\]]+                    {}
  \n                          { addToken(start,zzStartRead-1, Token.COMMENT_MULTILINE); return firstToken; }
  {LongStringEnd}             { yybegin(YYINITIAL); addToken(start,zzStartRead+1, Token.COMMENT_MULTILINE); }
  \]                          {}
  <<EOF>>                     { addToken(start,zzStartRead-1, Token.COMMENT_MULTILINE); return firstToken; }
}


<LONGSTRING> {
  [^\n\]]+                    {}
  \n                          { addToken(start,zzStartRead-1, Token.LITERAL_BACKQUOTE); return firstToken; }
  {LongStringEnd}             { yybegin(YYINITIAL); addToken(start,zzStartRead+1, Token.LITERAL_BACKQUOTE); }
  \]                          {}
  <<EOF>>                     { addToken(start,zzStartRead-1, Token.LITERAL_BACKQUOTE); return firstToken; }
}

<STRING> {
  [^\"\n]+                    {}
  {StringDelimiter}           { yybegin(YYINITIAL); addToken(start,zzStartRead, Token.LITERAL_STRING_DOUBLE_QUOTE); }
  \n                          { addToken(start,zzStartRead-1, Token.LITERAL_STRING_DOUBLE_QUOTE); return firstToken; }
  <<EOF>>                     { addToken(start,zzStartRead-1, Token.LITERAL_STRING_DOUBLE_QUOTE); return firstToken; }
}

<LINECOMMENT> {
  [^\n]+                      {}
  \n                          { addToken(start,zzStartRead-1, Token.COMMENT_EOL); return firstToken; }
  <<EOF>>                     { addToken(start,zzStartRead-1, Token.COMMENT_EOL); return firstToken; }
}
