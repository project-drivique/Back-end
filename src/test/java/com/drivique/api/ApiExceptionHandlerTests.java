package com.drivique.api;

import com.drivique.api.common.exception.ApiExceptionHandler;
import com.drivique.api.common.exception.ConflictException;
import com.drivique.api.common.exception.ResourceNotFoundException;
import com.drivique.api.config.ProblemSecurityHandler;
import com.drivique.api.config.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiExceptionHandlerTests.FailureController.class)
@Import({ApiExceptionHandlerTests.FailureController.class, ApiExceptionHandler.class,
        SecurityConfig.class, ProblemSecurityHandler.class})
class ApiExceptionHandlerTests {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @CsvSource({"missing,404", "conflict,409", "denied,403", "credentials,401", "unexpected,500"})
    @WithMockUser
    void mapsExceptionsToSafeProblems(String kind, int code) throws Exception {
        mvc.perform(get("/api/test-errors/" + kind).contextPath("/api").queryParam("token", "private-token"))
                .andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/test-errors/" + kind))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(content().string(not(containsString("sensitive-internal"))))
                .andExpect(content().string(not(containsString("private-token"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @WithMockUser
    void validationDoesNotExposeRejectedValuesOrCustomMessages() throws Exception {
        mvc.perform(post("/api/test-errors").contextPath("/api")
                        .contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"))
                .andExpect(content().string(not(containsString("sensitive-internal"))));
    }

    @Test
    @WithMockUser
    void malformedJsonReturns400InsteadOf500() throws Exception {
        mvc.perform(post("/api/test-errors").contextPath("/api")
                        .contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @WithMockUser
    void unsupportedMethodKeeps405AndAllowHeader() throws Exception {
        mvc.perform(put("/api/test-errors").contextPath("/api"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    @WithMockUser
    void missingRouteReturns404() throws Exception {
        mvc.perform(get("/api/no-such-route").contextPath("/api"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void securityFilterReturnsSameContractBeforeController() throws Exception {
        mvc.perform(get("/api/test-errors/missing").contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/test-errors/missing"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    // Fixtures exist only in test sources; no diagnostic endpoints ship in the API.
    @RestController
    @RequestMapping("/test-errors")
    static class FailureController {
        record Request(@NotBlank(message = "sensitive-internal") String name) {}

        @PostMapping
        void validate(@Valid @RequestBody Request request) {}

        @GetMapping("/{kind}")
        void fail(@PathVariable String kind) {
            throw switch (kind) {
                case "missing" -> new ResourceNotFoundException("sensitive-internal");
                case "conflict" -> new ConflictException("sensitive-internal");
                case "denied" -> new AccessDeniedException("sensitive-internal");
                case "credentials" -> new BadCredentialsException("sensitive-internal");
                default -> new IllegalStateException("sensitive-internal");
            };
        }
    }
}
