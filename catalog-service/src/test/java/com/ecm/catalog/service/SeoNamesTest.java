package com.ecm.catalog.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeoNamesTest {

    @Test
    void vietnameseLettersBecomeTheirPlainForm() {
        assertEquals("card-do-hoa-rog-strix", SeoNames.slug(null, "Card đồ họa ROG Strix"));
        assertEquals("phu-kien-chuot", SeoNames.slug("  ", "Phụ kiện: Chuột"));
        assertEquals("dong-ho", SeoNames.slug(null, "ĐỒNG HỒ"));
    }

    @Test
    void anExplicitSeoNameWinsAndIsCleanedToo() {
        assertEquals("my-slug", SeoNames.slug("  My Slug!  ", "Anything"));
        assertEquals("ban-phim", SeoNames.slug("Bàn phím", "Anything"));
    }

    @Test
    void aNameWithoutLettersGivesAnEmptySlug() {
        assertEquals("", SeoNames.slug(null, "!!!"));
    }
}
