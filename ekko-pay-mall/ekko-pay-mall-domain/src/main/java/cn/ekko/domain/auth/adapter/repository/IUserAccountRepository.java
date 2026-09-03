package cn.ekko.domain.auth.adapter.repository;

import cn.ekko.domain.auth.model.entity.UserAccountEntity;

/** 商城账号仓储。 */
public interface IUserAccountRepository {

    UserAccountEntity queryByUsername(String username);

    UserAccountEntity queryByUserId(String userId);

    /** 唯一键冲突时返回 false。 */
    boolean create(UserAccountEntity account);

    /** 修改密码并递增 tokenVersion，使已有 JWT 立即失效。 */
    int updatePassword(String userId, String passwordHash, int expectedTokenVersion);

    /** 递增 tokenVersion，用于服务端退出并使已有 JWT 立即失效。 */
    int revokeTokens(String userId, int expectedTokenVersion);
}
