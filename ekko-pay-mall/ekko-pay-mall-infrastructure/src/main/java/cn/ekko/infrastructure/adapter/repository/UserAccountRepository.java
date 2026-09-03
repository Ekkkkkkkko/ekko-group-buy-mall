package cn.ekko.infrastructure.adapter.repository;

import cn.ekko.domain.auth.adapter.repository.IUserAccountRepository;
import cn.ekko.domain.auth.model.entity.UserAccountEntity;
import cn.ekko.infrastructure.dao.IUserAccountDao;
import cn.ekko.infrastructure.dao.po.UserAccount;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository implements IUserAccountRepository {

    private final IUserAccountDao userAccountDao;

    public UserAccountRepository(IUserAccountDao userAccountDao) {
        this.userAccountDao = userAccountDao;
    }

    @Override
    public UserAccountEntity queryByUsername(String username) {
        return toEntity(userAccountDao.queryByUsername(username));
    }

    @Override
    public UserAccountEntity queryByUserId(String userId) {
        return toEntity(userAccountDao.queryByUserId(userId));
    }

    @Override
    public boolean create(UserAccountEntity account) {
        try {
            return 1 == userAccountDao.insert(UserAccount.builder()
                    .userId(account.getUserId())
                    .username(account.getUsername())
                    .passwordHash(account.getPasswordHash())
                    .status(account.getStatus())
                    .tokenVersion(account.getTokenVersion())
                    .build());
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    @Override
    public int updatePassword(String userId, String passwordHash, int expectedTokenVersion) {
        return userAccountDao.updatePassword(userId, passwordHash, expectedTokenVersion);
    }

    @Override
    public int revokeTokens(String userId, int expectedTokenVersion) {
        return userAccountDao.revokeTokens(userId, expectedTokenVersion);
    }

    private UserAccountEntity toEntity(UserAccount account) {
        if (null == account) return null;
        return UserAccountEntity.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .username(account.getUsername())
                .passwordHash(account.getPasswordHash())
                .status(account.getStatus())
                .tokenVersion(account.getTokenVersion())
                .build();
    }
}
