package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.InvalidInputRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Access to the {@code DQ_INVALID_INPUT} quarantine table. */
@Repository
public interface InvalidInputRepository extends JpaRepository<InvalidInputRecord, Long> {

  List<InvalidInputRecord> findAllByOrderByRecordedAtDescIdDesc();

  List<InvalidInputRecord> findByEntityTypeAndRecordKeyOrderByIdDesc(
      String entityType, String recordKey);

  List<InvalidInputRecord> findByRuleIdOrderByIdDesc(String ruleId);
}
