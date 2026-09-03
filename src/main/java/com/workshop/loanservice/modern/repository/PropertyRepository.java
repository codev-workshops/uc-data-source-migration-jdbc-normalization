package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, Long> {
}
