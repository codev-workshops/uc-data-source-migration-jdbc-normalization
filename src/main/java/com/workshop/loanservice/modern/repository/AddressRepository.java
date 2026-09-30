package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Address;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

  /** Exact five-field match used for address de-duplication; a null {@code line2} matches NULL. */
  Optional<Address> findFirstByLine1AndLine2AndCityAndStateCodeAndPostalCode(
      String line1, String line2, String city, String stateCode, String postalCode);
}
