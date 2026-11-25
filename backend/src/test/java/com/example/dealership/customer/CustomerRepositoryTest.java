package com.example.dealership.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class CustomerRepositoryTest {

    private static final String EMAIL = "marek.zielinski@example.com";
    private static final String OTHER_EMAIL = "ewa.zielinska@example.com";

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void existsByEmailAndIdNotFindsEmailOfAnotherCustomer() {
        Customer first = customerRepository.save(
                new Customer("Marek", "Zieliński", EMAIL, "600100200", null));
        Customer second = customerRepository.save(
                new Customer("Ewa", "Zielińska", OTHER_EMAIL, "600100201", null));
        entityManager.flush();

        assertThat(customerRepository.existsByEmailAndIdNot(EMAIL, second.getId())).isTrue();
        assertThat(customerRepository.existsByEmailAndIdNot(EMAIL, first.getId())).isFalse();
        assertThat(customerRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void saveStoresCustomerThatCanBeReadBack() {
        Customer saved = customerRepository.save(
                new Customer("Marek", "Zieliński", EMAIL, "+48 600 100 200", "7770001111"));
        entityManager.flush();
        entityManager.clear();

        Customer found = customerRepository.findById(saved.getId()).orElseThrow();

        assertThat(found).isNotSameAs(saved);
        assertThat(found.getFirstName()).isEqualTo("Marek");
        assertThat(found.getLastName()).isEqualTo("Zieliński");
        assertThat(found.getEmail()).isEqualTo(EMAIL);
        assertThat(found.getPhone()).isEqualTo("+48 600 100 200");
        assertThat(found.getNip()).isEqualTo("7770001111");
        assertThat(customerRepository.existsByEmail(EMAIL)).isTrue();
        assertThat(customerRepository.existsByEmailAndIdNot(EMAIL, saved.getId())).isFalse();
    }

    @Test
    void saveRejectsDuplicateEmail() {
        customerRepository.saveAndFlush(new Customer("Marek", "Zieliński", EMAIL, "600100200", null));
        Customer duplicate = new Customer("Ewa", "Zielińska", EMAIL, "600100201", null);

        assertThatThrownBy(() -> customerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
