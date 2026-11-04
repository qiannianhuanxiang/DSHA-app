package com.deepseekharness.app.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserThemeColorTest {
  @Test
  public void computedColorsAndHexAreParsedWithoutAndroidColorParser() {
    assertEquals(
        Integer.valueOf(0xff102030), BrowserThemeColor.parse("rgb(16, 32, 48)", 0xffffffff));
    assertEquals(Integer.valueOf(0xffaabbcc), BrowserThemeColor.parse("#AbC", 0));
    assertEquals(Integer.valueOf(0xffabcdef), BrowserThemeColor.parse("#abcdef", 0));
    assertEquals(Integer.valueOf(0xff000000), BrowserThemeColor.parse("rgb(0,0,0)", 0));
  }

  @Test
  public void AlphaCompositesAndTransparentOrInvalidKeepsPrevious() {
    assertEquals(
        Integer.valueOf(0xff808080), BrowserThemeColor.parse("rgba(0,0,0,.5)", 0xffffffff));
    for (String color :
        new String[] {
          null,
          "transparent",
          "rgb(999,0,0)",
          "rgba(1,2,3,2)",
          "rgba(0,0,0,0)",
          "#xyz",
          "#00000000",
          "red",
          "rgb(-1,0,0)"
        }) assertNull(String.valueOf(color), BrowserThemeColor.parse(color, 0xffffffff));
  }
}
