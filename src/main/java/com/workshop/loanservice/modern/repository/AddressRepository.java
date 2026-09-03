package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}
