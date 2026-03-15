package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import per.nonobeam.common.config.JobTracking;

public interface CommonJobTrackingRepository extends JpaRepository<JobTracking, String> {}
