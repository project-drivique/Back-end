package com.drivique.api.notifications;

import com.drivique.api.model.NotificationTemplate;
import com.drivique.api.repository.NotificationTemplateRepository;
import com.drivique.api.service.notification.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceTests {

    @Mock
    private NotificationTemplateRepository templateRepository;

    private NotificationTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new NotificationTemplateService(templateRepository);
    }

    @Test
    void render_ReplacesPlaceholdersWithValues() {
        String template = "Hola {{firstName}}, tu reserva {{bookingCode}} ha sido confirmada.";
        Map<String, Object> vars = Map.of(
                "firstName", "Carlos",
                "bookingCode", "RES-9988"
        );

        String result = templateService.render(template, vars);

        assertThat(result).isEqualTo("Hola Carlos, tu reserva RES-9988 ha sido confirmada.");
    }

    @Test
    void render_MissingVariablesReplacedWithEmpty() {
        String template = "Vehículo {{licensePlate}} en {{branchName}}.";
        Map<String, Object> vars = Map.of("licensePlate", "ABC-123");

        String result = templateService.render(template, vars);

        assertThat(result).isEqualTo("Vehículo ABC-123 en .");
    }

    @Test
    void findTemplate_RetrievesByEventAndChannel() {
        NotificationTemplate tmpl = new NotificationTemplate(
                "REG_WELCOME", "Bienvenida", "REGISTRATION", "EMAIL", "Asunto", "Cuerpo", true
        );

        when(templateRepository.findByEventTypeAndChannelAndIsActiveTrue("REGISTRATION", "EMAIL"))
                .thenReturn(Optional.of(tmpl));

        Optional<NotificationTemplate> result = templateService.findTemplate("REGISTRATION", "EMAIL");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("REG_WELCOME");
    }
}
