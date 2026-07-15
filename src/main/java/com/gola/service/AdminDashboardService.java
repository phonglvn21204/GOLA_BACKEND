package com.gola.service;

import com.gola.dto.admin.AdminDashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardSummaryResponse summary() {
        return AdminDashboardSummaryResponse.builder()
            .totalRevenue(number("""
                SELECT COALESCE(SUM(amount), 0)
                FROM orders
                WHERE status::text = 'SUCCEEDED'
                """))
            .totalUsers(number("SELECT COUNT(*) FROM profiles WHERE deleted_at IS NULL"))
            .totalTransactions(number("SELECT COUNT(*) FROM orders"))
            .totalReviews(number("SELECT COUNT(*) FROM reviews"))
            .totalTrips(number("SELECT COUNT(*) FROM trips WHERE deleted_at IS NULL"))
            .totalCompletedQuests(number("SELECT COUNT(*) FROM quest_progress WHERE status::text = 'COMPLETED'"))
            .totalPosts(number("SELECT COUNT(*) FROM posts"))
            .totalComments(number("SELECT COUNT(*) FROM comments"))
            .openReports(number("SELECT COUNT(*) FROM reports WHERE status = 'OPEN'"))
            .revenueByMonth(list("""
                SELECT to_char(date_trunc('month', COALESCE(paid_at, created_at)), 'YYYY-MM') AS month,
                       COALESCE(SUM(amount), 0) AS revenue
                FROM orders
                WHERE status::text = 'SUCCEEDED'
                GROUP BY 1
                ORDER BY 1 DESC
                LIMIT 12
                """))
            .transactionsByStatus(list("""
                SELECT status::text AS status, COUNT(*) AS count
                FROM orders
                GROUP BY status::text
                ORDER BY count DESC
                """))
            .usersByMonth(list("""
                SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month,
                       COUNT(*) AS users
                FROM profiles
                WHERE deleted_at IS NULL
                GROUP BY 1
                ORDER BY 1 DESC
                LIMIT 12
                """))
            .topQuestTypes(list("""
                SELECT q.type::text AS type, COUNT(*) AS completed
                FROM quest_progress qp
                JOIN quests q ON q.id = qp.quest_id
                WHERE qp.status::text = 'COMPLETED'
                GROUP BY q.type::text
                ORDER BY completed DESC
                LIMIT 8
                """))
            .recentTransactions(list("""
                SELECT o.id, o.amount, o.currency, o.status::text AS status,
                       o.payment_provider AS provider,
                       COALESCE(o.transfer_content, o.order_code, o.stripe_session_id) AS reference,
                       o.created_at, o.paid_at,
                       p.email, p.display_name AS user_name
                FROM orders o
                LEFT JOIN profiles p ON p.id = o.user_id
                ORDER BY o.created_at DESC
                LIMIT 8
                """))
            .recentReviews(list("""
                SELECT r.id, r.rating, r.body, r.status::text AS status, r.created_at,
                       p.display_name AS user_name,
                       COALESCE(r.place_name, pl.name, t.title, 'Chuyến đi') AS place_name
                FROM reviews r
                LEFT JOIN profiles p ON p.id = r.user_id
                LEFT JOIN places pl ON pl.id = r.place_id
                LEFT JOIN trips t ON t.id = r.trip_id
                ORDER BY r.created_at DESC
                LIMIT 8
                """))
            .recentQuestCompletions(list("""
                SELECT qp.id, qp.completed_at, q.title AS quest_title, q.type::text AS type,
                       t.title AS trip_title, ts.name AS stop_name,
                       p.display_name AS user_name
                FROM quest_progress qp
                LEFT JOIN quests q ON q.id = qp.quest_id
                LEFT JOIN trips t ON t.id = qp.trip_id
                LEFT JOIN trip_stops ts ON ts.id = qp.trip_stop_id
                LEFT JOIN profiles p ON p.id = qp.user_id
                WHERE qp.status::text = 'COMPLETED'
                ORDER BY qp.completed_at DESC NULLS LAST, qp.updated_at DESC NULLS LAST
                LIMIT 8
                """))
            .recentCommunityReports(list("""
                SELECT r.id, r.target_type, r.target_id, r.reason, r.status,
                       r.created_at, p.email, p.display_name AS reporter_name
                FROM reports r
                LEFT JOIN profiles p ON p.id = r.reporter_id
                ORDER BY r.created_at DESC
                LIMIT 8
                """))
            .build();
    }

    private long number(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private List<Map<String, Object>> list(String sql) {
        return jdbcTemplate.queryForList(sql);
    }
}
