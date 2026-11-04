package com.moabom.grants;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryAccountRepository implements AccountRepository {
    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<String, Account> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> loginToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> emailToId = new ConcurrentHashMap<>();

    @Override
    public Account insertNew(Account draft) {
        synchronized (this) {
            rejectDuplicates(draft.loginId(), draft.email(), null);
            String userId = Long.toString(nextId.getAndIncrement());
            Account stored = draft.withUserId(userId);
            keep(stored);
            return stored;
        }
    }

    @Override
    public void insertPreservingId(Account account) {
        synchronized (this) {
            if (account.userId() == null || !ApiResponses.ID.matcher(account.userId()).matches()) {
                throw new IllegalArgumentException("사용자 ID 형식이 올바르지 않습니다.");
            }
            if (byId.containsKey(account.userId())) {
                throw new AccountConflictException("이미 사용 중인 사용자 ID입니다.");
            }
            rejectDuplicates(account.loginId(), account.email(), null);
            keep(account);
            long numeric = Long.parseLong(account.userId());
            nextId.updateAndGet(current -> Math.max(current, numeric + 1));
        }
    }

    @Override
    public Optional<Account> findByLoginId(String loginId) {
        String userId = loginToId.get(loginId);
        return userId == null ? Optional.empty() : Optional.ofNullable(byId.get(userId));
    }

    @Override
    public Optional<Account> findByUserId(String userId) {
        return Optional.ofNullable(byId.get(userId));
    }

    @Override
    public boolean loginIdExists(String loginId) {
        return loginToId.containsKey(loginId);
    }

    @Override
    public void replace(Account account) {
        synchronized (this) {
            Account current = byId.get(account.userId());
            if (current == null) {
                throw new IllegalArgumentException("인증된 사용자를 찾을 수 없습니다.");
            }
            if (!current.loginId().equals(account.loginId())) {
                throw new IllegalArgumentException("아이디는 변경할 수 없습니다.");
            }
            rejectDuplicates(account.loginId(), account.email(), account.userId());
            if (!current.email().equals(account.email())) {
                emailToId.remove(current.email(), account.userId());
            }
            byId.put(account.userId(), account);
            emailToId.put(account.email(), account.userId());
        }
    }

    private void rejectDuplicates(String loginId, String email, String ownerId) {
        String loginOwner = loginToId.get(loginId);
        if (loginOwner != null && !loginOwner.equals(ownerId)) {
            throw new AccountConflictException("이미 사용 중인 아이디입니다.");
        }
        String emailOwner = emailToId.get(email);
        if (emailOwner != null && !emailOwner.equals(ownerId)) {
            throw new AccountConflictException("이미 사용 중인 이메일입니다.");
        }
    }

    private void keep(Account account) {
        byId.put(account.userId(), account);
        loginToId.put(account.loginId(), account.userId());
        emailToId.put(account.email(), account.userId());
    }
}
