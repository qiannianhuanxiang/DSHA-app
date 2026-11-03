package com.deepseekharness.app.util;

import static org.junit.Assert.*;
import org.junit.Test;

public final class AccessibilityTreeBudgetTest {
  @Test
  public void emptySparseTreeCannotTraverseAnUnboundedChildList() {
    var budget = new AccessibilityTreeBudget();
    assertTrue(budget.enter(0, 0));
    int nullChildren = 0;
    while (budget.childSlot(0)) nullChildren++;
    assertEquals(4095, nullChildren);
    assertTrue(budget.exhausted());
    assertFalse(budget.enter(1, 0));
  }

  @Test
  public void depthAndOutputLimitsPreserveZeroOutputBudget() {
    var budget = new AccessibilityTreeBudget();
    assertFalse(budget.enter(25, 0));
    assertFalse(budget.enter(0, 200));
    assertFalse(budget.childSlot(200));
    assertEquals(0, budget.visits());
    assertTrue(budget.enter(24, 199));
  }
}
