package com.moabom.grants;

import java.util.Optional;

public interface AccountRepository {
    Account insertNew(Account draft);

    void insertPreservingId(Account account);

    Optional<Account> findByLoginId(String loginId);

    Optional<Account> findByUserId(String userId);

    boolean loginIdExists(String loginId);

    void replace(Account account);
}
