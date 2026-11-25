package com.example.dealership.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final String EMAIL = "jan.kowalski@example.com";
    private static final String OTHER_EMAIL = "anna.nowak@example.com";
    private static final String NIP = "1234563218";

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void findAllReturnsMappedCustomers() {
        when(customerRepository.findAll(Sort.by("id"))).thenReturn(List.of(customer(EMAIL), customer(OTHER_EMAIL)));

        List<CustomerResponse> responses = customerService.findAll();

        assertThat(responses).extracting(CustomerResponse::email).containsExactly(EMAIL, OTHER_EMAIL);
    }

    @Test
    void findByIdThrowsNotFoundWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }

    @Test
    void createSavesCustomer() {
        when(customerRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(request(EMAIL));

        assertThat(response.firstName()).isEqualTo("Jan");
        assertThat(response.lastName()).isEqualTo("Kowalski");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.phone()).isEqualTo("+48 600 100 200");
        assertThat(response.nip()).isEqualTo(NIP);
    }

    @Test
    void createSavesCustomerWithoutNip() {
        when(customerRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(
                new CustomerRequest("Jan", "Kowalski", EMAIL, "+48 600 100 200", null));

        assertThat(response.nip()).isNull();
    }

    @Test
    void createNormalizesEmailBeforeCheckingAndSaving() {
        when(customerRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(request("  Jan.Kowalski@EXAMPLE.com "));

        assertThat(response.email()).isEqualTo(EMAIL);
        verify(customerRepository).existsByEmail(EMAIL);
    }

    @Test
    void createThrowsConflictWhenEmailAlreadyExists() {
        when(customerRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(request("Jan.Kowalski@Example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Customer with email " + EMAIL + " already exists");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateChangesExistingCustomer() {
        Customer customer = customer(EMAIL);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.existsByEmailAndIdNot(OTHER_EMAIL, 1L)).thenReturn(false);
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerResponse response = customerService.update(1L, request("Anna.Nowak@Example.com"));

        assertThat(response.email()).isEqualTo(OTHER_EMAIL);
        assertThat(customer.getEmail()).isEqualTo(OTHER_EMAIL);
        verify(customerRepository).save(customer);
    }

    @Test
    void updateThrowsConflictWhenEmailBelongsToAnotherCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(EMAIL)));
        when(customerRepository.existsByEmailAndIdNot(OTHER_EMAIL, 1L)).thenReturn(true);

        assertThatThrownBy(() -> customerService.update(1L, request("ANNA.NOWAK@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Customer with email " + OTHER_EMAIL + " already exists");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateThrowsNotFoundWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(99L, request(EMAIL)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }

    @Test
    void deleteRemovesExistingCustomer() {
        Customer customer = customer(EMAIL);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        customerService.delete(1L);

        verify(customerRepository).delete(customer);
    }

    @Test
    void deleteThrowsNotFoundWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.delete(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Customer with id 99 not found");
    }

    private static Customer customer(String email) {
        return new Customer("Jan", "Kowalski", email, "+48 600 100 200", NIP);
    }

    private static CustomerRequest request(String email) {
        return new CustomerRequest("Jan", "Kowalski", email, "+48 600 100 200", NIP);
    }
}
