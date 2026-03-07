package com.prbotbenchmark.runner;

import com.prbotbenchmark.runner.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    boolean existsByDeliveryId(String deliveryId);

    List<WebhookEvent> findByRepoFullNameAndPrNumber(String repoFullName, Integer prNumber);
}
