package cn.ekko.groupchat.auth.mapper;

import cn.ekko.groupchat.auth.entity.AdminUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 管理员账号 Mapper，继承 MyBatis-Plus {@link BaseMapper}，
 * 额外提供按账号名查询单条记录的方法。
 */
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    @Select("""
            SELECT id, username, password_hash, enabled
            FROM admin_user
            WHERE username = #{username}
            LIMIT 1
            """)
    AdminUser selectByUsername(@Param("username") String username);
}
