package com.qaplatform.android.automation.service.run;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suite-level twin of {@link RunCancellationRegistry}. Kept separate so the
 * orchestrators can poll their own scope without ambiguity (run id and
 * suite-run id namespaces could otherwise collide).
 */
@Component
public class SuiteRunCancellationRegistry {

    private final Set<Long> cancelled = ConcurrentHashMap.newKeySet();

    public void requestCancel(long suiteRunId) { cancelled.add(suiteRunId); }
    public boolean isCancelled(long suiteRunId) { return cancelled.contains(suiteRunId); }
    public void clear(long suiteRunId) { cancelled.remove(suiteRunId); }
}
