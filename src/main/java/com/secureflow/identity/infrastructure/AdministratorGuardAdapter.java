package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.AdministratorGuardPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class AdministratorGuardAdapter implements AdministratorGuardPort {
    private final JdbcTemplate jdbc;

    AdministratorGuardAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Transaction required");
        Integer id = jdbc.queryForObject("select id from administrator_guard where id = 1 for update", Integer.class);
        if (id == null || id != 1) throw new IllegalStateException("Administrator guard unavailable");
    }

    @Override
    public long countActiveAdministrators() {
        return jdbc.queryForObject(
                "select count(*) from users u join user_roles r on r.user_id = u.id where u.status = 'ACTIVE' and r.role = 'ADMIN'",
                Long.class);
    }
}
