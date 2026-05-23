package com.qaplatform._template.automation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Automation service entry point for the <strong>_template</strong> platform.
 *
 * <p>Rename to {@code {Platform}AutomationApplication} when copying. The
 * {@code @EnableAsync} annotation is here so the reports publisher you'll
 * copy from the Android stack works out of the box without re-enabling
 * async on every platform.</p>
 */
@SpringBootApplication
@EnableAsync
public class TemplateAutomationApplication {
    public static void main(String[] args) {
        SpringApplication.run(TemplateAutomationApplication.class, args);
    }
}
