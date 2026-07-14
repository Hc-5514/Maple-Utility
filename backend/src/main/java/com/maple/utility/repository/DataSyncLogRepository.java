package com.maple.utility.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.maple.utility.entity.DataSyncLog;

public interface DataSyncLogRepository extends JpaRepository<DataSyncLog, Long> {
}
