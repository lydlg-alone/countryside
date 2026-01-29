package org.village.user.service;

import org.village.user.model.User;
import org.village.logging.AuditService;

public class UserService {
    private final AuditService audit = new AuditService();

    public User getById(Long id) {
        // placeholder: normally fetch from DB
        audit.record("system", "getUser", "id=" + id);
        return new User(id, "user" + id, "姓名" + id);
    }
}
