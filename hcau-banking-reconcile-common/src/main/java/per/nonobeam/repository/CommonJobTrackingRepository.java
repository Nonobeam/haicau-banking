package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import per.nonobeam.common.config.JobTracking;
import per.nonobeam.common.config.JobTrackingId;

public interface CommonJobTrackingRepository extends JpaRepository<JobTracking, JobTrackingId> {}
