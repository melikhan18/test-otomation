package com.devicefarm.automation.locator;

/** {strategy, value} pair, serialized in the fallback_locators JSON array. */
public record Locator(LocatorStrategy strategy, String value) {}
