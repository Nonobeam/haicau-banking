package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import per.nonobeam.common.config.JobConfig;

public interface CommonJobConfigRepository extends JpaRepository<JobConfig, String> {
}
