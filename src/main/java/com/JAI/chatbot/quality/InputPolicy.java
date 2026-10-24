package com.JAI.chatbot.quality;
import java.util.regex.Pattern;
/** Conservative checks only for explicit, unambiguous evidence of unsupported/ambiguous input. */
public final class InputPolicy {
    private InputPolicy() { }
    private static final Pattern REPEAT=Pattern.compile("매일|매주|매월|매년|격주|반복|(?i:every\\s+(day|week|month|year)|daily|weekly)");
    private static final Pattern BARE_HOUR=Pattern.compile("(?<![0-9])(?:[1-9]|1[0-2])시(?!간)");
    private static final Pattern PERIOD=Pattern.compile("오전|오후|새벽|아침|낮|저녁|밤|정오|자정|(?i:am|pm)");
    public static void validate(String input) {
        if (REPEAT.matcher(input).find()) throw new QualityException(QualityError.UNSUPPORTED_RECURRENCE,"recurrence");
        if (BARE_HOUR.matcher(input).find() && !PERIOD.matcher(input).find())
            throw new QualityException(QualityError.AMBIGUOUS_INPUT,"AM/PM");
    }
}
