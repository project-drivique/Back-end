package com.drivique.api.service.notification;

import com.drivique.api.model.NotificationTemplate;
import com.drivique.api.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationTemplateService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplateService(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public Optional<NotificationTemplate> findTemplate(String eventType, String channel) {
        if (eventType == null || channel == null) {
            return Optional.empty();
        }
        return templateRepository.findByEventTypeAndChannelAndIsActiveTrue(
                eventType.trim().toUpperCase(),
                channel.trim().toUpperCase()
        );
    }

    public Optional<NotificationTemplate> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return templateRepository.findByCodeAndIsActiveTrue(code.trim());
    }

    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
