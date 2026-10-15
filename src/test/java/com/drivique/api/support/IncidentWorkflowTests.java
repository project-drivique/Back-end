package com.drivique.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.drivique.api.service.IncidentWorkflow;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IncidentWorkflowTests {

    @Test
    void criticalKeywordsForceCriticalPriority() {
        var result = IncidentWorkflow.classify("OTHER", "Tuve un accidente en la vía", null);
        assertThat(result.priority()).isEqualTo("CRITICAL");
        assertThat(result.sla()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void accentsAndCaseAreIgnoredWhenMatchingKeywords() {
        assertThat(IncidentWorkflow.classify("VEHICLE", "COLISIÓN con otro auto", null).priority()).isEqualTo("CRITICAL");
    }

    @Test
    void vehicleInUseEscalatesOneLevel() {
        assertThat(IncidentWorkflow.classify("VEHICLE", "Ruido extraño", "CONFIRMED").priority()).isEqualTo("MEDIUM");
        assertThat(IncidentWorkflow.classify("VEHICLE", "Ruido extraño", "IN_PROGRESS").priority()).isEqualTo("HIGH");
    }

    @Test
    void genericQuestionsAreLowPriority() {
        var result = IncidentWorkflow.classify("OTHER", "Pregunta sobre horarios", null);
        assertThat(result.priority()).isEqualTo("LOW");
        assertThat(result.sla()).isEqualTo(Duration.ofHours(72));
    }

    @Test
    void transitionsDependOnWhoOperates() {
        assertThat(IncidentWorkflow.canTransition("OPEN", "IN_REVIEW", true)).isTrue();
        assertThat(IncidentWorkflow.canTransition("OPEN", "IN_REVIEW", false)).isFalse();
        assertThat(IncidentWorkflow.canTransition("RESOLVED", "CLOSED", false)).isTrue();
        assertThat(IncidentWorkflow.canTransition("CLOSED", "OPEN", true)).isFalse();
        assertThat(IncidentWorkflow.canTransition("IN_REVIEW", "OPEN", true)).isFalse();
    }
}
