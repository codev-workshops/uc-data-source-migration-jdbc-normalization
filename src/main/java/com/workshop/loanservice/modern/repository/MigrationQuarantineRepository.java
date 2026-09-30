package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.MigrationQuarantine;
import com.workshop.loanservice.modern.entity.QuarantineReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationQuarantineRepository extends JpaRepository<MigrationQuarantine, Long> {

  List<MigrationQuarantine> findByReasonCode(QuarantineReason reasonCode);
}
