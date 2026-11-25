package com.example.dealership.document;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.example.dealership.error.NotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VehicleDocumentController.class)
class VehicleDocumentControllerTest {

    private static final String NUMBER = "OC/2026/123456";
    private static final String VALID_BODY = """
            {
              "type": "OC_POLICY",
              "number": "OC/2026/123456",
              "issueDate": "2026-11-01",
              "expiryDate": "2027-10-31"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleDocumentService vehicleDocumentService;

    @Test
    void findAllReturnsDocumentsOfVehicle() throws Exception {
        when(vehicleDocumentService.findAll(1L)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/vehicles/1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].vehicleId").value(1))
                .andExpect(jsonPath("$[0].type").value("OC_POLICY"))
                .andExpect(jsonPath("$[0].number").value(NUMBER))
                .andExpect(jsonPath("$[0].issueDate").value("2026-11-01"))
                .andExpect(jsonPath("$[0].expiryDate").value("2027-10-31"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void findAllReturnsNotFoundProblemWhenVehicleDoesNotExist() throws Exception {
        when(vehicleDocumentService.findAll(99L)).thenThrow(new NotFoundException("Vehicle with id 99 not found"));

        mockMvc.perform(get("/api/vehicles/99/documents"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Vehicle with id 99 not found"));
    }

    @Test
    void createReturnsCreatedDocument() throws Exception {
        when(vehicleDocumentService.create(eq(1L), any(VehicleDocumentRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/vehicles/1/documents/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.vehicleId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(vehicleDocumentService).create(1L, new VehicleDocumentRequest(
                DocumentType.OC_POLICY, NUMBER, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31)));
    }

    @Test
    void createReturnsCreatedDocumentWhenOptionalFieldsAreMissing() throws Exception {
        when(vehicleDocumentService.create(eq(1L), any(VehicleDocumentRequest.class))).thenReturn(response());
        String body = """
                { "type": "COC", "number": "COC/2025/000001", "issueDate": "2025-11-20" }
                """;

        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        verify(vehicleDocumentService).create(1L, new VehicleDocumentRequest(
                DocumentType.COC, "COC/2025/000001", LocalDate.of(2025, 11, 20), null));
    }

    @Test
    void createIgnoresStatusSentInBody() throws Exception {
        when(vehicleDocumentService.create(eq(1L), any(VehicleDocumentRequest.class))).thenReturn(response());
        String body = VALID_BODY.replace("\"type\"", "\"status\": \"VERIFIED\", \"type\"");

        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(vehicleDocumentService).create(1L, new VehicleDocumentRequest(
                DocumentType.OC_POLICY, NUMBER, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31)));
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorsWhenRequestIsInvalid() throws Exception {
        String body = """
                { "type": "OC_POLICY", "number": "%s", "expiryDate": "2027-10-31" }
                """.formatted("x".repeat(51));

        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("issueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"))
                .andExpect(jsonPath("$.errors[1].field").value("number"))
                .andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 50"));
        verifyNoInteractions(vehicleDocumentService);
    }

    @Test
    void createReturnsBadRequestProblemWithAllRequiredFieldsWhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("issueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"))
                .andExpect(jsonPath("$.errors[1].field").value("number"))
                .andExpect(jsonPath("$.errors[1].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[2].field").value("type"))
                .andExpect(jsonPath("$.errors[2].message").value("must not be null"));
        verifyNoInteractions(vehicleDocumentService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenTypeIsUnknown() throws Exception {
        String body = VALID_BODY.replace("OC_POLICY", "PASSPORT");

        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be one of: SALES_AGREEMENT, INVOICE, COC, OC_POLICY, REGISTRATION_POA"));
        verifyNoInteractions(vehicleDocumentService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenDateHasWrongFormat() throws Exception {
        String body = VALID_BODY.replace("2026-11-01", "01.11.2026");

        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("issueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
        verifyNoInteractions(vehicleDocumentService);
    }

    @Test
    void createReturnsBadRequestProblemWhenBodyIsNotJson() throws Exception {
        mockMvc.perform(post("/api/vehicles/1/documents").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").value("request body must be valid JSON"));
        verifyNoInteractions(vehicleDocumentService);
    }

    @Test
    void createReturnsNotFoundProblemWhenVehicleDoesNotExist() throws Exception {
        when(vehicleDocumentService.create(eq(99L), any(VehicleDocumentRequest.class)))
                .thenThrow(new NotFoundException("Vehicle with id 99 not found"));

        mockMvc.perform(post("/api/vehicles/99/documents").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Vehicle with id 99 not found"));
    }

    private static VehicleDocumentResponse response() {
        return new VehicleDocumentResponse(5L, 1L, DocumentType.OC_POLICY, NUMBER, LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 10, 31), DocumentStatus.PENDING);
    }
}
