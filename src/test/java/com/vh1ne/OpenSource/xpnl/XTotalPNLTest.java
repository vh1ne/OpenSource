package com.vh1ne.OpenSource.xpnl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XTotalPNLTest {
    @Test
    void calculateSummaryTest()
    {
        var publicURL = "https://web.sensibull.com/verified-pnl/loud-malachite";
        XTotalPNL.calculateSummary(publicURL);
        assertTrue(true);
    }

}