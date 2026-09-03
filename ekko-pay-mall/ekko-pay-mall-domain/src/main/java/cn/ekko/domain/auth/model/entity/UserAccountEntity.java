package cn.ekko.domain.auth.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 商城账号；passwordHash 只保存 BCrypt 哈希，不保存明文密码。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountEntity {

    public static final int ENABLED = 1;

    private Long id;
    private String userId;
    private String username;
    private String passwordHash;
    private Integer status;
    private Integer tokenVersion;

    public boolean isEnabled() {
        return Integer.valueOf(ENABLED).equals(status);
    }
}
