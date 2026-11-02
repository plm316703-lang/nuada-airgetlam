package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearcherAssignmentRepository extends JpaRepository<ResearcherAssignment, Long> {

    boolean existsByPatientAndResearcher(Patient patient, User researcher);
}
