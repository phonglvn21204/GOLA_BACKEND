package com.gola.service;

import com.gola.dto.quest.RedemptionRequest;
import com.gola.entity.Redemption;
import com.gola.entity.Reward;
import com.gola.entity.Wallet;
import com.gola.entity.enums.RedemptionStatus;
import com.gola.exception.GolaException;
import com.gola.repository.RedemptionRepository;
import com.gola.repository.RewardRepository;
import com.gola.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedemptionService {

    private final RedemptionRepository redemptionRepo;
    private final RewardRepository rewardRepo;
    private final WalletRepository walletRepo;

    @Transactional
    public Redemption redeemReward(UUID userId, RedemptionRequest req) {
        Reward reward = rewardRepo.findById(req.getRewardId())
                .orElseThrow(() -> GolaException.notFound("Reward"));
        
        if (!reward.isActive() || (reward.getStock() == 0)) {
            throw GolaException.badRequest("Reward is out of stock or inactive");
        }

        Wallet wallet = walletRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> GolaException.notFound("Wallet"));

        if (wallet.getGolaCoins() < reward.getCostCoins()) {
            throw GolaException.badRequest("Insufficient Gola coins");
        }

        wallet.setGolaCoins(wallet.getGolaCoins() - reward.getCostCoins());
        walletRepo.save(wallet);

        if (reward.getStock() > 0) {
            reward.setStock(reward.getStock() - 1);
            rewardRepo.save(reward);
        }

        Redemption redemption = Redemption.builder()
                .userId(userId)
                .rewardId(reward.getId())
                .code(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(RedemptionStatus.PENDING)
                .redeemedAt(Instant.now())
                .build();

        return redemptionRepo.save(redemption);
    }
}
