package org.jaudiotagger.audio.real;

import org.jaudiotagger.audio.generic.GenericTag;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.TagField;

public class RealTag extends GenericTag {
  public String toString() {
      return "REAL " + super.toString();
  }

  public TagField createCompilationField(boolean value) throws KeyNotFoundException, FieldDataInvalidException {
    return createField(FieldKey.IS_COMPILATION, String.valueOf(value));
  }
}
