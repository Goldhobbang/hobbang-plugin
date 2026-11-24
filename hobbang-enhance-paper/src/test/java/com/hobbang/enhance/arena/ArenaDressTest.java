package com.hobbang.enhance.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArenaDressTest {

    @Test
    void altarCoreIncludedEvenWhenKeepBlockIsTrue() {
        // BLAST_FURNACE 는 StormArena.keepBlock 에서 true 이므로, isCore=true 일 때 반드시 포함되어야 한다
        assertTrue(ArenaDress.shouldIncludeAltarArea(false, true, true), "altarCore must be included even if keepBlock=true");
        assertFalse(ArenaDress.shouldIncludeAltarArea(false, false, true), "non-core keepBlock must be skipped");
        assertTrue(ArenaDress.shouldIncludeAltarArea(false, false, false), "3x3 pedestal block must be included");
        assertFalse(ArenaDress.shouldIncludeAltarArea(true, true, true), "air block must be skipped");
    }
}
