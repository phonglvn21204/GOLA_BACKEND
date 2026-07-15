package com.gola.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AdminDashboardSummaryResponse {
    private long totalRevenue;
    private long totalUsers;
    private long totalTransactions;
    private long totalReviews;
    private long totalTrips;
    private long totalCompletedQuests;
    private long totalPosts;
    private long totalComments;
    private long openReports;
    private List<Map<String, Object>> revenueByMonth;
    private List<Map<String, Object>> transactionsByStatus;
    private List<Map<String, Object>> usersByMonth;
    private List<Map<String, Object>> topQuestTypes;
    private List<Map<String, Object>> recentTransactions;
    private List<Map<String, Object>> recentReviews;
    private List<Map<String, Object>> recentQuestCompletions;
    private List<Map<String, Object>> recentCommunityReports;
}
