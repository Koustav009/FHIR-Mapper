/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.common.helpers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

  @Override
  public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {

    String value = parser.getText();

    try {
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException ignored) {
      return LocalDate.parse(value).atStartOfDay();
    }
  }
}
