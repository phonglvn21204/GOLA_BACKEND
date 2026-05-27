package com.gola.repository;
import com.gola.entity.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository
public interface PriceRepository extends JpaRepository<Price, UUID> {
    List<Price> findByProductIdAndIsActiveTrueOrderByAmountAsc(UUID productId);
    List<Price> findByIsActiveTrueOrderByAmountAsc();
}
