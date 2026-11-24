package com.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vault.rules.WarrantyCalculator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WarrantyCalculatorTest {
    private final LocalDate purchase = LocalDate.of(2025, 1, 1);

    @Test
    void standardWarranty() {
        var result = WarrantyCalculator.calculate(
                "Main Unit", "STANDARD", purchase, 12, 0, LocalDate.of(2025, 6, 1));
        assertEquals(LocalDate.of(2026, 1, 1), result.expiryDate());
        assertEquals("ACTIVE", result.status());
        assertEquals(0, result.replacementDiscount());
    }

    @Test
    void proratedDiscount() {
        var result = WarrantyCalculator.calculate(
                "Battery", "PRORATED", purchase, 12, 1000, LocalDate.of(2025, 7, 2));
        assertEquals("PRORATED", result.type());
        assertTrue(result.replacementDiscount() > 0 && result.replacementDiscount() < 1000);
    }
}
