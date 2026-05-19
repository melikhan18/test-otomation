package com.devicefarm.automation.locator;

/**
 * How an element is identified on the device's accessibility tree.
 * Ordering reflects general robustness (RESOURCE_ID is most reliable, XPATH the most fragile).
 */
public enum LocatorStrategy {
    RESOURCE_ID,
    ACCESSIBILITY_ID,    // contentDescription
    TEXT,
    CLASS,
    XPATH
}
