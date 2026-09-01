package com.example.demo.auth;

import com.example.demo.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Why {@code REQUIRES_NEW} must not be reached by a self-call.
 * <p>
 * {@code @Transactional} is executed by a proxy that stands outside the bean. A call that
 * starts inside the bean - {@code this.method()} - never crosses that proxy, so the annotation
 * on the target is skipped with no error and no warning: the work silently joins the caller's
 * transaction and dies with it. The identical method reached through a different bean does get
 * its own transaction and survives.
 * <p>
 * Both writes below are annotated {@code REQUIRES_NEW} and both run inside a transaction that
 * then throws. Only the one routed through another bean is still in the table afterwards.
 */
@SpringBootTest
@Import({PostgresTestContainer.class, SelfInvocationTransactionDemoTest.Beans.class})
class SelfInvocationTransactionDemoTest {

    @Autowired
    private Outer outer;
    @Autowired
    private JdbcTemplate jdbc;

    // tasks.owner_id became a NOT NULL foreign key, so the two writes below need a real owner.
    // Nothing here is about ownership - it is the cheapest row that satisfies the constraint.
    private static final UUID OWNER = UUID.fromString("01f11400-0000-7000-8000-00000000dec0");

    @BeforeEach
    void clean() {
        jdbc.execute("truncate table users cascade");
        jdbc.update("""
                insert into users (id, username, email, password_hash, role, enabled)
                values (?, 'demo', 'demo@example.com', 'x', 'USER', true)""", OWNER);
    }

    @Test
    void requiresNewSurvivesRollbackOnlyWhenReachedThroughAnotherBean() {
        assertThatThrownBy(() -> outer.doWork())
                .isInstanceOf(IllegalStateException.class);

        assertThat(rowsTitled("SELF"))
                .as("self-invoked REQUIRES_NEW: proxy bypassed, joined the outer transaction, rolled back")
                .isZero();
        assertThat(rowsTitled("BEAN"))
                .as("REQUIRES_NEW through another bean: own transaction, committed independently")
                .isOne();
    }

    private Integer rowsTitled(String title) {
        return jdbc.queryForObject("select count(*) from tasks where title = ?", Integer.class, title);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        Inner inner(JdbcTemplate jdbc) {
            return new Inner(jdbc);
        }

        @Bean
        Outer outer(JdbcTemplate jdbc, Inner inner) {
            return new Outer(jdbc, inner);
        }
    }

    /** A separate bean, so calls into it go through its own proxy. */
    static class Inner {
        private final JdbcTemplate jdbc;

        Inner(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void write(String title) {
            jdbc.update("insert into tasks (title, owner_id) values (?, ?)", title, OWNER);
        }
    }

    static class Outer {
        private final JdbcTemplate jdbc;
        private final Inner inner;

        Outer(JdbcTemplate jdbc, Inner inner) {
            this.jdbc = jdbc;
            this.inner = inner;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void write(String title) {
            jdbc.update("insert into tasks (title, owner_id) values (?, ?)", title, OWNER);
        }

        @Transactional
        public void doWork() {
            write("SELF");        // this.write(..) - proxy not in the path, annotation ignored
            inner.write("BEAN");  // through Inner's proxy - genuinely a new transaction
            throw new IllegalStateException("boom");
        }
    }
}
