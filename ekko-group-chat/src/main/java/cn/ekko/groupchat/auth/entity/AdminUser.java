package cn.ekko.groupchat.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员账号实体，映射 {@code admin_user} 表，
 * 存储账号名、BCrypt 密码哈希及启用状态。
 */
@TableName("admin_user")
@Getter
@Setter
public class AdminUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private Boolean enabled;

}
