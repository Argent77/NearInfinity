// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter.creature;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/** Localized text used by the creature animation creator's structured editors. */
final class CreatureAnimationMessages {
  private static final String BUNDLE_NAME =
      "org.infinity.gui.converter.creature.CreatureAnimationMessages";

  private CreatureAnimationMessages() {
  }

  static String get(String key) {
    return get(Locale.getDefault(), key);
  }

  static String get(Locale locale, String key) {
    final Locale effectiveLocale = locale != null ? locale : Locale.getDefault();
    return ResourceBundle.getBundle(BUNDLE_NAME, effectiveLocale).getString(key);
  }

  static String format(String key, Object... arguments) {
    final Locale locale = Locale.getDefault();
    return new MessageFormat(get(locale, key), locale).format(arguments);
  }
}
