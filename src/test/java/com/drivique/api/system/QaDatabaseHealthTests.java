package com.drivique.api.system;

import com.drivique.api.DatabaseHealthTestSupport;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("qa")
class QaDatabaseHealthTests extends DatabaseHealthTestSupport {
}
