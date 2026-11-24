package com.vault.rules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class WarrantyCalculator {
    public enum ReminderTier {
        SILENT,
        TIER_1_ADVISORY,
        TIER_2_HIGH_PRIORITY,
        TIER_3_CRITICAL,
        EXPIRED_ARCHIVED_NO_ALERTS
    }

    private WarrantyCalculator() {}

    public record Result(
            String name,
            String type,
            LocalDate expiryDate,
            long remainingDays,
            String status,
            double replacementDiscount,
            ReminderTier reminderTier) {}

    public static ReminderTier classifyReminderTier(long remainingDays) {
        if (remainingDays < 0) {
            return ReminderTier.EXPIRED_ARCHIVED_NO_ALERTS;
        }
        if (remainingDays > 30) {
            return ReminderTier.SILENT;
        }
        if (remainingDays >= 15) {
            return ReminderTier.TIER_1_ADVISORY;
        }
        if (remainingDays >= 4) {
            return ReminderTier.TIER_2_HIGH_PRIORITY;
        }
        if (remainingDays <= 3) {
            return ReminderTier.TIER_3_CRITICAL;
        }
        return ReminderTier.SILENT;
    }

    public static Result calculate(
            String name,
            String type,
            LocalDate purchaseDate,
            int durationMonths,
            double replacementValue,
            LocalDate today) {
        if (durationMonths < 0) {
            throw new IllegalArgumentException("durationMonths must be non-negative");
        }

        String normalized = "PRORATED".equalsIgnoreCase(type) ? "PRORATED" : "STANDARD";
        LocalDate expiry = purchaseDate.plusMonths(durationMonths);
        long days = ChronoUnit.DAYS.between(today, expiry);
        String status = days < 0 ? "EXPIRED" : days <= 30 ? "EXPIRING_SOON" : "ACTIVE";
        double discount = 0;

        if (normalized.equals("PRORATED") && replacementValue > 0 && durationMonths > 0) {
            long used = Math.max(0, ChronoUnit.DAYS.between(purchaseDate, today));
            double elapsed = Math.min(
                    1.0,
                    (double) used / Math.max(1, ChronoUnit.DAYS.between(purchaseDate, expiry)));
            discount = Math.max(0, Math.min(replacementValue, replacementValue * (1.0 - elapsed)));
        }

        return new Result(
                name,
                normalized,
                expiry,
                days,
                status,
                Math.round(discount * 100.0) / 100.0,
                classifyReminderTier(days));
    }
}
