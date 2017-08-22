package utils

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * Qualifier for System Tests:
 *
 * - [tagged][Tag] as `system-test`
 * - [test instance][TestInstance] generation: `PER_METHOD`
 */
@Retention
@Target(AnnotationTarget.CLASS)
@Tag("system-test")
@TestInstance(PER_METHOD)
annotation class SystemTest