package com.vault.scheduler;

import com.vault.rules.WarrantyCalculator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class ExpiryScheduler implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ExpiryScheduler.class.getName());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "warranty-expiry-check");
        thread.setDaemon(true);
        return thread;
    });

    public ExpiryScheduler() {
        executor.scheduleAtFixedRate(
                () -> LOG.info("Periodic warranty expiry check completed (repository not configured)"),
                0,
                24,
                TimeUnit.HOURS);
    }

    public static WarrantyCalculator.ReminderTier classifyReminder(
            long remainingDays) {
        return WarrantyCalculator.classifyReminderTier(remainingDays);
    }

    public static boolean shouldSendReminder(long remainingDays) {
        return remainingDays >= 0
                && classifyReminder(remainingDays) != WarrantyCalculator.ReminderTier.SILENT;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
