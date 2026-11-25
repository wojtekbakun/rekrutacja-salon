package com.example.dealership.customer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final String EMAIL = "jan.kowalski@example.com";
    private static final String NIP = "1234563218";
    private static final String VALID_BODY = """
            {
              "firstName": "Jan",
              "lastName": "Kowalski",
              "email": "jan.kowalski@example.com",
              "phone": "+48 600 100 200",
              "nip": "1234563218"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void findAllReturnsCustomers() throws Exception {
        when(customerService.findAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Jan"))
                .andExpect(jsonPath("$[0].lastName").value("Kowalski"))
                .andExpect(jsonPath("$[0].email").value(EMAIL))
                .andExpect(jsonPath("$[0].phone").value("+48 600 100 200"))
                .andExpect(jsonPath("$[0].nip").value(NIP));
    }

    @Test
    void findByIdReturnsCustomer() throws Exception {
        when(customerService.findById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void findByIdReturnsNotFoundProblemWhenCustomerDoesNotExist() throws Exception {
        when(customerService.findById(99L)).thenThrow(new NotFoundException("Customer with id 99 not found"));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Customer with id 99 not found"));
    }

    @Test
    void createReturnsCreatedCustomer() throws Exception {
        when(customerService.create(any(CustomerRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/customers/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createReturnsCreatedCustomerWhenOptionalFieldsAreMissing() throws Exception {
        String body = """
                {
                  "firstName": "Jan",
                  "lastName": "Kowalski",
                  "email": "jan.kowalski@example.com",
                  "phone": "+48 600 100 200"
                }
                """;
        when(customerService.create(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(1L, "Jan", "Kowalski", EMAIL, "+48 600 100 200", null));

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nip").isEmpty());
        verify(customerService).create(new CustomerRequest("Jan", "Kowalski", EMAIL, "+48 600 100 200", null));
    }

    @Test
    void createTrimsEmailBeforeValidation() throws Exception {
        String body = VALID_BODY.replace("\"" + EMAIL + "\"", "\"  Jan.Kowalski@Example.com \"");
        when(customerService.create(any(CustomerRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(customerService).create(
                new CustomerRequest("Jan", "Kowalski", "Jan.Kowalski@Example.com", "+48 600 100 200", NIP));
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorsWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(invalidBody()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a well-formed email address"))
                .andExpect(jsonPath("$.errors[1].field").value("firstName"))
                .andExpect(jsonPath("$.errors[1].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[2].field").value("lastName"))
                .andExpect(jsonPath("$.errors[2].message").value("size must be between 0 and 50"))
                .andExpect(jsonPath("$.errors[3].field").value("nip"))
                .andExpect(jsonPath("$.errors[3].message").value("must be 10 digits with a valid NIP checksum"))
                .andExpect(jsonPath("$.errors[4].field").value("phone"))
                .andExpect(jsonPath("$.errors[4].message")
                        .value("must be 9 to 15 digits, optionally starting with + and separated by single spaces"));
        verifyNoInteractions(customerService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorsWhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[1].field").value("firstName"))
                .andExpect(jsonPath("$.errors[2].field").value("lastName"))
                .andExpect(jsonPath("$.errors[3].field").value("phone"))
                .andExpect(jsonPath("$.errors[3].message").value("must not be blank"));
        verifyNoInteractions(customerService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenEmailIsTooLong() throws Exception {
        String body = VALID_BODY.replace(EMAIL, "a@" + "b".repeat(50) + "." + "c".repeat(50) + ".pl");

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 100"));
        verifyNoInteractions(customerService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenPhoneHasTooManyDigits() throws Exception {
        String body = VALID_BODY.replace("+48 600 100 200", "+48 600 100 200 300 45");

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be 9 to 15 digits, optionally starting with + and separated by single spaces"));
        verifyNoInteractions(customerService);
    }

    @Test
    void createReturnsBadRequestProblemWithFieldErrorWhenNipChecksumIsInvalid() throws Exception {
        String body = VALID_BODY.replace(NIP, "1234563210");

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("nip"))
                .andExpect(jsonPath("$.errors[0].message").value("must be 10 digits with a valid NIP checksum"));
        verifyNoInteractions(customerService);
    }

    @Test
    void createReturnsConflictProblemWhenEmailAlreadyExists() throws Exception {
        when(customerService.create(any(CustomerRequest.class)))
                .thenThrow(new ConflictException("Customer with email " + EMAIL + " already exists"));

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Customer with email " + EMAIL + " already exists"));
    }

    @Test
    void updateReturnsUpdatedCustomer() throws Exception {
        when(customerService.update(eq(1L), any(CustomerRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void updateReturnsNotFoundProblemWhenCustomerDoesNotExist() throws Exception {
        when(customerService.update(eq(99L), any(CustomerRequest.class)))
                .thenThrow(new NotFoundException("Customer with id 99 not found"));

        mockMvc.perform(put("/api/customers/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Customer with id 99 not found"));
    }

    @Test
    void updateReturnsBadRequestProblemWithFieldErrorsWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON).content(invalidBody()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("firstName"))
                .andExpect(jsonPath("$.errors[2].field").value("lastName"))
                .andExpect(jsonPath("$.errors[3].field").value("nip"))
                .andExpect(jsonPath("$.errors[4].field").value("phone"));
        verifyNoInteractions(customerService);
    }

    @Test
    void updateReturnsConflictProblemWhenEmailAlreadyExists() throws Exception {
        when(customerService.update(eq(1L), any(CustomerRequest.class)))
                .thenThrow(new ConflictException("Customer with email " + EMAIL + " already exists"));

        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Customer with email " + EMAIL + " already exists"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent());

        verify(customerService).delete(1L);
    }

    @Test
    void deleteReturnsNotFoundProblemWhenCustomerDoesNotExist() throws Exception {
        doThrow(new NotFoundException("Customer with id 99 not found")).when(customerService).delete(99L);

        mockMvc.perform(delete("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Customer with id 99 not found"));
    }

    private static String invalidBody() {
        return """
                {
                  "firstName": "",
                  "lastName": "%s",
                  "email": "not-an-email",
                  "phone": "12345",
                  "nip": "1234563210"
                }
                """.formatted("x".repeat(51));
    }

    private static CustomerResponse response() {
        return new CustomerResponse(1L, "Jan", "Kowalski", EMAIL, "+48 600 100 200", NIP);
    }
}
