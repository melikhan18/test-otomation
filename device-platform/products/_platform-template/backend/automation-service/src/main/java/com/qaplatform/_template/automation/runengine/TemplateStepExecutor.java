package com.qaplatform._template.automation.runengine;

import com.qaplatform.common.runengine.spi.RunStep;
import com.qaplatform.common.runengine.spi.StepContext;
import com.qaplatform.common.runengine.spi.StepExecutor;
import com.qaplatform.common.runengine.spi.StepOutcome;
import org.springframework.stereotype.Component;

/**
 * The single integration point with the platform-agnostic run engine
 * (see {@link com.qaplatform.common.runengine}).
 *
 * <p>Replace the body with your platform's dispatch. Typical shape:</p>
 *
 * <pre>{@code
 * switch (step.action()) {
 *     case "OPEN_URL"     -> openUrl(parse(step.payload()).url, ctx);
 *     case "CLICK"        -> click(parse(step.payload()).selector, ctx);
 *     case "ASSERT_TEXT"  -> assertText(parse(step.payload()).selector,
 *                                       parse(step.payload()).expected, ctx);
 *     ...
 *     default             -> StepOutcome.error("unknown action: " + step.action());
 * }
 * }</pre>
 *
 * <p>Guidelines:</p>
 * <ul>
 *   <li>Poll {@link StepContext#cancel()} during long waits — the
 *       orchestrator counts on it for bounded cancellation latency.</li>
 *   <li>Emit screenshot bytes on FAILED / ERROR via
 *       {@link StepOutcome#failed(String, byte[])} — the orchestrator
 *       uploads them via {@link StepContext#artifacts()}.</li>
 *   <li>Never throw for expected failures (assertion miss → return
 *       {@code StepOutcome.failed(reason)}, not an exception). Throw
 *       only for truly unexpected conditions (driver crash, transport
 *       error).</li>
 * </ul>
 */
@Component
public class TemplateStepExecutor implements StepExecutor {

    @Override
    public StepOutcome execute(RunStep step, StepContext ctx) {
        ctx.log().info("step " + step.orderIndex() + " (" + step.action() + ") — _template skeleton, no executor wired");
        return StepOutcome.error(
                "TemplateStepExecutor is not implemented — "
                + "wire your platform's step dispatch here (see javadoc)");
    }
}
