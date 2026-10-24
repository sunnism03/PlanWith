package com.JAI.chatbot.quality;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
/** Deliberately narrow: checks a single unqualified relative day, not arbitrary Korean semantics. */
public final class RelativeDateGuard {
    private RelativeDateGuard() { }
    public static void validate(String input, List<LocalDate> dates, Clock clock) {
        var matcher = Pattern.compile("(?<![가-힣])(오늘|내일|모레|어제)(?![가-힣])").matcher(input);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) matches.add(matcher.group());
        // Multiple dates/ranges need richer interpretation; do not falsely reject them.
        if (matches.size() != 1 || dates.size() != 1 || input.matches("(?s).*(부터|까지|다음|이번|[0-9]|요일).*")) return;
        int offset = switch(matches.get(0)) { case "내일" -> 1; case "모레" -> 2; case "어제" -> -1; default -> 0; };
        if (!LocalDate.now(clock).plusDays(offset).equals(dates.get(0)))
            throw new QualityException(QualityError.SEMANTIC_MISMATCH, "relativeDate");
    }
}
